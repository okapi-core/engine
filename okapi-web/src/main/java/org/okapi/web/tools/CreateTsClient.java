/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CreateTsClient scans Java Spring @RestController classes in a base package via reflection and
 * generates a TypeScript API client file with named-argument functions.
 *
 * <p>Usage: java org.okapi.web.tools.CreateTsClient <basePackage>
 *
 * <p>Convention: output is written to ./api.ts in the working directory.
 */
@Slf4j
public class CreateTsClient {

  public static void main(String[] args) throws Exception {
    log.info(Arrays.toString(args));
    if (args == null || args.length < 1 || args[0] == null || args[0].isBlank()) {
      System.err.println("Usage: CreateTsClient <basePackage>");
      return;
    }
    String basePackage = args[0].trim();
    Path outputTs = Paths.get("api.ts");

    Set<Class<?>> classes = findClasses(basePackage);
    List<Endpoint> endpoints = new ArrayList<>();
    for (Class<?> cls : classes) {
      if (!cls.isAnnotationPresent(RestController.class)) continue;
      String classPrefix = extractClassPrefix(cls);
      for (Method m : cls.getDeclaredMethods()) {
        Endpoint ep = extractEndpointFromMethod(classPrefix, m);
        if (ep != null) endpoints.add(ep);
      }
    }

    String ts = generateTs(endpoints);
    Files.writeString(
        outputTs,
        ts,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("Generated TS client: " + outputTs.toAbsolutePath());
  }

  // ===== Data structures =====

  static class Endpoint {
    String httpMethod; // GET/POST
    String fullPath; // with class prefix + method mapping
    String methodName; // Java method name -> TS function name
    String returnType; // generic return type name
    List<Param> params = new ArrayList<>();
  }

  static class Param {
    String javaType;
    String name; // TS/Java variable name (derived)
    boolean isPathVar;
    String pathVarName; // name inside {...} if provided
    boolean isRequestBody;
  }

  // ===== Reflection helpers =====

  private static String extractClassPrefix(Class<?> cls) {
    RequestMapping rm = cls.getAnnotation(RequestMapping.class);
    if (rm == null) return "";
    String p = firstNonEmpty(rm.path());
    if (p == null) p = firstNonEmpty(rm.value());
    return p == null ? "" : p;
  }

  private static Endpoint extractEndpointFromMethod(String classPrefix, Method method) {
    String httpMethod = null;
    String path = null;

    GetMapping gm = method.getAnnotation(GetMapping.class);
    PostMapping pm = method.getAnnotation(PostMapping.class);
    RequestMapping rm = method.getAnnotation(RequestMapping.class);

    if (gm != null) {
      httpMethod = "GET";
      path = firstNonEmpty(gm.path());
      if (path == null) path = firstNonEmpty(gm.value());
    } else if (pm != null) {
      httpMethod = "POST";
      path = firstNonEmpty(pm.path());
      if (path == null) path = firstNonEmpty(pm.value());
    } else if (rm != null) {
      RequestMethod[] methods = rm.method();
      if (methods != null && methods.length > 0) {
        httpMethod = methods[0].name();
      } else {
        httpMethod = "GET";
      }
      path = firstNonEmpty(rm.path());
      if (path == null) path = firstNonEmpty(rm.value());
    } else {
      return null;
    }

    Endpoint ep = new Endpoint();
    ep.httpMethod = httpMethod;
    ep.fullPath = mergePaths(classPrefix, path);
    ep.methodName = method.getName();
    ep.returnType = method.getGenericReturnType().getTypeName();
    ep.params = parseParams(method.getParameters());
    return ep;
  }

  private static String firstNonEmpty(String[] arr) {
    if (arr == null) return null;
    for (String s : arr) {
      if (s != null && !s.isBlank()) return s;
    }
    return null;
  }

  private static List<Param> parseParams(Parameter[] parameters) {
    List<Param> out = new ArrayList<>();
    for (Parameter p0 : parameters) {
      Param p = new Param();
      p.javaType = p0.getParameterizedType().getTypeName();
      p.name = p0.isNamePresent() ? p0.getName() : defaultParamName(p0);

      PathVariable pv = p0.getAnnotation(PathVariable.class);
      if (pv != null) {
        p.isPathVar = true;
        String n = (pv.name() != null && !pv.name().isBlank()) ? pv.name() : pv.value();
        p.pathVarName = (n != null && !n.isBlank()) ? n : null;
      }

      if (p0.getAnnotation(RequestBody.class) != null) {
        p.isRequestBody = true;
        if (!p0.isNamePresent()) p.name = "request";
      }

      if (p.isPathVar && (p.pathVarName == null || p.pathVarName.isBlank())) {
        p.pathVarName = p.name;
      }

      out.add(p);
    }
    return out;
  }

  private static String defaultParamName(Parameter p0) {
    // Reasonable defaults when -parameters is not enabled
    if (p0.getAnnotation(RequestHeader.class) != null) return "header";
    if (p0.getAnnotation(RequestBody.class) != null) return "request";
    if (p0.getAnnotation(PathVariable.class) != null) return "param";
    return "arg";
  }

  // ===== TS generation =====

  private static String generateTs(List<Endpoint> endpoints) {
    StringBuilder sb = new StringBuilder();

    // Collect imports
    Set<String> reqTypes = new TreeSet<>();
    Set<String> resTypes = new TreeSet<>();
    boolean useGetWOT = false, usePostWOT = false;

    for (Endpoint ep : endpoints) {
      mapReturnType(ep.returnType, resTypes);
      if ("GET".equals(ep.httpMethod)) {
        useGetWOT = true;
      } else {
        usePostWOT = true;
        ep.params.stream()
            .filter(p -> p.isRequestBody)
            .findFirst()
            .ifPresent(p -> mapJavaToTsType(p.javaType, reqTypes, resTypes));
      }
      for (Param p : ep.params) {
        mapJavaToTsType(p.javaType, reqTypes, resTypes);
      }
    }

    // Imports
    List<String> apiFns = new ArrayList<>();
    if (usePostWOT) apiFns.add("postWithoutToken");
    if (useGetWOT) apiFns.add("getWithoutToken");
    // Always need ApiResponse wrapper
    sb.append("import { ApiResponse } from './api-responses';\n");
    if (!apiFns.isEmpty()) {
      sb.append("import { ")
          .append(String.join(", ", apiFns))
          .append(" } from './api-common';\n\n");
    }
    if (!reqTypes.isEmpty()) {
      sb.append("import { ")
          .append(String.join(", ", reqTypes))
          .append(" } from './request-types';\n");
    }
    if (!resTypes.isEmpty()) {
      sb.append("import { ")
          .append(String.join(", ", resTypes))
          .append(" } from './response-types';\n");
    }
    if (!apiFns.isEmpty() || !reqTypes.isEmpty() || !resTypes.isEmpty()) sb.append('\n');

    // Functions
    for (Endpoint ep : endpoints) {
      sb.append(renderFunction(ep, reqTypes, resTypes));
      sb.append("\n");
    }

    return sb.toString();
  }

  private static String renderFunction(Endpoint ep, Set<String> reqTypes, Set<String> resTypes) {
    StringBuilder sb = new StringBuilder();

    String tsReturn = mapReturnType(ep.returnType, resTypes);

    List<String> argNames = new ArrayList<>();
    List<String> argTypes = new ArrayList<>();

    for (Param p : ep.params) {
      if (p.isPathVar) {
        String name = p.pathVarName != null ? p.pathVarName : p.name;
        argNames.add(name);
        argTypes.add(name + ": " + mapJavaToTsType(p.javaType, reqTypes, resTypes));
      }
    }

    Param body = ep.params.stream().filter(pp -> pp.isRequestBody).findFirst().orElse(null);
    if (body != null) {
      argNames.add(body.name);
      argTypes.add(body.name + ": " + mapJavaToTsType(body.javaType, reqTypes, resTypes));
    }

    sb.append("export async function ")
        .append(ep.methodName)
        .append("({")
        .append(String.join(", ", argNames))
        .append("}: {")
        .append(argTypes.isEmpty() ? "" : "\n  ")
        .append(String.join(";\n  ", argTypes))
        .append(argTypes.isEmpty() ? "" : "\n")
        .append("}): Promise<ApiResponse<")
        .append(tsReturn)
        .append(">> {\n");

    String url = toTemplateLiteral(ep.fullPath, ep.params);

    if ("GET".equals(ep.httpMethod)) {
      sb.append("  return await getWithoutToken<")
          .append(tsReturn)
          .append(">({ url: `")
          .append(url)
          .append("` });\n");
    } else { // POST
      String reqType = body != null ? mapJavaToTsType(body.javaType, reqTypes, resTypes) : "any";
      sb.append("  return await postWithoutToken<")
          .append(reqType)
          .append(", ")
          .append(tsReturn)
          .append(">({ url: `")
          .append(url)
          .append("`, request: ")
          .append(body != null ? body.name : "undefined")
          .append(" });\n");
    }
    sb.append("}\n");
    return sb.toString();
  }

  private static String toTemplateLiteral(String path, List<Param> params) {
    String out = path;
    for (Param p : params) {
      if (p.isPathVar) {
        String name = p.pathVarName != null ? p.pathVarName : p.name;
        out = out.replace("{" + name + "}", "${" + name + "}");
      }
    }
    return out;
  }

  private static String mapReturnType(String javaTypeName, Set<String> resTypes) {
    String t = javaTypeName.trim();
    if (t.equals("void") || t.equals("java.lang.Void") || t.equals("Void")) return "void";
    if (t.equals("java.lang.String") || t.equals("String")) return "string";
    if (t.startsWith("java.util.List<")
        || t.startsWith("java.util.Collection<")
        || t.startsWith("List<")) {
      String inner = t.substring(t.indexOf('<') + 1, t.lastIndexOf('>')).trim();
      String tsInner = mapJavaToTsType(inner, new TreeSet<>(), resTypes);
      return tsInner + "[]";
    }
    if (t.startsWith(ResponseEntity.class.getName() + "<") || t.startsWith("ResponseEntity<")) {
      String inner = t.substring(t.indexOf('<') + 1, t.lastIndexOf('>')).trim();
      return mapJavaToTsType(inner, new TreeSet<>(), resTypes);
    }
    String simple = simpleName(t);
    if (simple.endsWith("Response")
        || simple.endsWith("Dto")
        || simple.endsWith("WDto")
        || simple.endsWith("View")) {
      resTypes.add(simple);
      return simple;
    }
    return mapJavaToTsType(t, new TreeSet<>(), resTypes);
  }

  private static String mapJavaToTsType(
      String javaType, Set<String> reqTypes, Set<String> resTypes) {
    String t = javaType.trim();
    if ((t.startsWith("java.util.List<")
            || t.startsWith("java.util.Collection<")
            || t.startsWith("List<"))
        && t.endsWith(">")) {
      String inner = t.substring(t.indexOf('<') + 1, t.length() - 1).trim();
      return mapJavaToTsType(inner, reqTypes, resTypes) + "[]";
    }
    switch (t) {
      case "byte":
      case "short":
      case "int":
      case "long":
      case "float":
      case "double":
      case "java.lang.Byte":
      case "java.lang.Short":
      case "java.lang.Integer":
      case "java.lang.Long":
      case "java.lang.Float":
      case "java.lang.Double":
      case "Byte":
      case "Short":
      case "Integer":
      case "Long":
      case "Float":
      case "Double":
        return "number";
      case "boolean":
      case "java.lang.Boolean":
      case "Boolean":
        return "boolean";
      case "java.lang.String":
      case "String":
        return "string";
      case "void":
      case "java.lang.Void":
      case "Void":
        return "void";
      default:
        String simple = simpleName(t);
        if (simple.endsWith("Request")) {
          reqTypes.add(simple);
          return simple;
        }
        if (simple.endsWith("Response")
            || simple.endsWith("Dto")
            || simple.endsWith("WDto")
            || simple.endsWith("View")) {
          resTypes.add(simple);
          return simple;
        }
        return "any";
    }
  }

  private static String simpleName(String t) {
    int lt = t.lastIndexOf('<');
    if (lt > 0) t = t.substring(0, lt);
    int dot = t.lastIndexOf('.');
    if (dot >= 0) return t.substring(dot + 1);
    return t;
  }

  private static String mergePaths(String prefix, String path) {
    String p1 = prefix == null ? "" : prefix.trim();
    String p2 = path == null ? "" : path.trim();
    if (p1.isEmpty() && p2.isEmpty()) return "/";
    if (p1.isEmpty()) return normalizePath(p2);
    if (p2.isEmpty()) return normalizePath(p1);
    String a = p1.endsWith("/") ? p1.substring(0, p1.length() - 1) : p1;
    String b = p2.startsWith("/") ? p2.substring(1) : p2;
    return normalizePath(a + "/" + b);
  }

  private static String normalizePath(String p) {
    if (p == null || p.isEmpty()) return "/";
    String out = p.startsWith("/") ? p : "/" + p;
    return out.replaceAll("/+", "/");
  }

  // ===== Classpath scanning (ported style from CreateTsTypeFiles) =====
  private static Set<Class<?>> findClasses(String basePackage)
      throws IOException, URISyntaxException {
    Set<Class<?>> classes = new HashSet<>();
    String path = basePackage.replace('.', '/');
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Enumeration<URL> resources = cl.getResources(path);
    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();
      String protocol = resource.getProtocol();
      if ("file".equals(protocol)) {
        File dir = new File(decode(resource.getPath()));
        if (dir.exists() && dir.isDirectory()) {
          findClassesInDirectory(basePackage, dir, classes);
        }
      } else if ("jar".equals(protocol)) {
        JarURLConnection conn = (JarURLConnection) resource.openConnection();
        try (JarFile jar = conn.getJarFile()) {
          Enumeration<JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith(path) && name.endsWith(".class") && !name.contains("$")) {
              String className = name.replace('/', '.').substring(0, name.length() - 6);
              tryAdd(classes, className);
            }
          }
        }
      }
    }
    return classes;
  }

  private static void findClassesInDirectory(
      String basePackage, File directory, Set<Class<?>> classes) {
    File[] files = directory.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file.isDirectory()) {
        String subPkg = basePackage + "." + file.getName();
        findClassesInDirectory(subPkg, file, classes);
      } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
        String className = toClassName(basePackage, file.getName());
        tryAdd(classes, className);
      }
    }
  }

  private static void tryAdd(Set<Class<?>> classes, String className) {
    try {
      Class<?> cls = Class.forName(className);
      classes.add(cls);
    } catch (Throwable ignored) {
      // ignore classes that fail to load
    }
  }

  private static String toClassName(String basePackage, String fileName) {
    String simple = fileName.substring(0, fileName.length() - 6); // drop .class
    return basePackage + "." + simple;
  }

  private static String decode(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }
}
