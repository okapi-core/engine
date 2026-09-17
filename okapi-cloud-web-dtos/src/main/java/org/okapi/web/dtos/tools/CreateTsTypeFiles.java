/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.tools;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class CreateTsTypeFiles {

  // Base package must be supplied via CLI arg
  private static final String NOT_NULL_ANNOTATION = "jakarta.validation.constraints.NotNull";

  public static void main(String[] args) {
    try {
      if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
        System.out.println("Usage: TypeScriptTypeCreator <base-package-to-scan>");
        System.exit(2);
        return;
      }

      String basePackage = args[0].trim();

      Set<Class<?>> allClasses = findClasses(basePackage);
      // Identify top-level DTOs: end with Request or Response
      List<Class<?>> requestRoots = new ArrayList<>();
      List<Class<?>> responseRoots = new ArrayList<>();
      for (Class<?> cls : allClasses) {
        String simple = cls.getSimpleName();
        if (cls.isEnum()) continue; // enums are pulled in when referenced
        if (simple.endsWith("Request")) {
          requestRoots.add(cls);
        } else if (simple.endsWith("Response")) {
          responseRoots.add(cls);
        }
      }

      TypePartitions partitions = new TypePartitions(requestRoots, responseRoots);
      String requestOutput =
          generateTypeScript(requestRoots, basePackage, TypeFile.REQUEST, partitions);
      String responseOutput =
          generateTypeScript(responseRoots, basePackage, TypeFile.RESPONSE, partitions);

      Path reqPath = Paths.get("request-types.ts");
      Path respPath = Paths.get("response-types.ts");
      Files.writeString(reqPath, requestOutput);
      Files.writeString(respPath, responseOutput);

      System.out.println("Wrote TypeScript request types to: " + reqPath.toAbsolutePath());
      System.out.println("Wrote TypeScript response types to: " + respPath.toAbsolutePath());
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Failed to generate TypeScript types: " + e.getMessage());
      System.exit(1);
    }
  }

  private enum TypeFile {
    REQUEST,
    RESPONSE
  }

  private static class TypePartitions {
    private final Set<Class<?>> requestTypes;
    private final Set<Class<?>> responseTypes;

    TypePartitions(List<Class<?>> requestTypes, List<Class<?>> responseTypes) {
      this.requestTypes = new HashSet<>(requestTypes);
      this.responseTypes = new HashSet<>(responseTypes);
    }

    TypeFile ownerOf(Class<?> cls) {
      if (requestTypes.contains(cls)) return TypeFile.REQUEST;
      if (responseTypes.contains(cls)) return TypeFile.RESPONSE;
      return null;
    }
  }

  private static String generateTypeScript(
      List<Class<?>> roots, String basePackage, TypeFile currentFile, TypePartitions partitions) {
    StringBuilder sb = new StringBuilder();
    // Use stable ordering of outputs
    Map<String, String> definitions = new TreeMap<>();
    Map<String, String> imports = new TreeMap<>();
    Set<Class<?>> visited = new HashSet<>();
    Deque<Class<?>> queue = new ArrayDeque<>(roots);

    while (!queue.isEmpty()) {
      Class<?> cls = queue.removeFirst();
      if (visited.contains(cls)) continue;
      visited.add(cls);

      TypeFile owner = partitions.ownerOf(cls);
      if (owner != null && owner != currentFile) {
        imports.put(cls.getSimpleName(), importPath(owner));
        continue;
      }

      if (cls.isEnum()) {
        String def = renderEnum(cls);
        definitions.put(cls.getSimpleName(), def);
        continue;
      }

      // Only handle classes within base package; skip JDK and other libs
      if (!cls.getName().startsWith(basePackage)) {
        continue;
      }

      String def = renderInterface(cls, queue, basePackage, currentFile, partitions, imports);
      definitions.put(cls.getSimpleName(), def);
    }

    for (Map.Entry<String, String> entry : imports.entrySet()) {
      sb.append("import { ")
          .append(entry.getKey())
          .append(" } from '")
          .append(entry.getValue())
          .append("';\n");
    }
    if (!imports.isEmpty()) sb.append("\n");

    for (String key : definitions.keySet()) {
      sb.append(definitions.get(key)).append("\n");
    }
    return sb.toString();
  }

  private static String importPath(TypeFile typeFile) {
    return switch (typeFile) {
      case REQUEST -> "./request-types";
      case RESPONSE -> "./response-types";
    };
  }

  private static String renderEnum(Class<?> enumClass) {
    Object[] constants = enumClass.getEnumConstants();
    List<String> parts = new ArrayList<>();
    for (Object c : constants) {
      parts.add("\"" + c.toString() + "\"");
    }
    return "export type " + enumClass.getSimpleName() + " = " + String.join(" | ", parts) + ";";
  }

  private static String renderInterface(
      Class<?> cls,
      Deque<Class<?>> queue,
      String basePackage,
      TypeFile currentFile,
      TypePartitions partitions,
      Map<String, String> imports) {
    StringBuilder sb = new StringBuilder();
    sb.append("export interface ").append(cls.getSimpleName()).append(" {\n");

    for (Field f : cls.getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
      String tsType =
          mapJavaTypeToTs(f.getGenericType(), queue, basePackage, currentFile, partitions, imports);
      boolean required = hasNotNull(f.getAnnotations());
      sb.append("  ")
          .append(f.getName())
          .append(required ? "" : "?")
          .append(": ")
          .append(tsType)
          .append(";\n");
    }

    sb.append("}\n");
    return sb.toString();
  }

  private static boolean hasNotNull(Annotation[] annotations) {
    for (Annotation a : annotations) {
      if (a.annotationType().getName().equals(NOT_NULL_ANNOTATION)) {
        return true;
      }
    }
    return false;
  }

  private static String mapJavaTypeToTs(
      Type type,
      Deque<Class<?>> queue,
      String basePackage,
      TypeFile currentFile,
      TypePartitions partitions,
      Map<String, String> imports) {
    if (type instanceof Class) {
      Class<?> cls = (Class<?>) type;
      if (cls.isArray()) {
        String elem =
            mapJavaTypeToTs(
                cls.getComponentType(), queue, basePackage, currentFile, partitions, imports);
        return elem + "[]";
      }
      // primitives and common types
      if (cls.equals(String.class) || cls.equals(CharSequence.class)) return "string";
      if (cls.equals(boolean.class) || cls.equals(Boolean.class)) return "boolean";
      if (isNumeric(cls)) return "number";
      if (cls.getName().startsWith("java.time.")) return "string";
      if (cls.getName().equals("java.lang.Object")) return "any";

      if (cls.isEnum()) {
        // Enqueue to ensure enum type alias gets emitted
        queue.addLast(cls);
        return cls.getSimpleName();
      }

      // If the class is within our base package, enqueue for interface generation
      if (cls.getName().startsWith(basePackage)) {
        TypeFile owner = partitions.ownerOf(cls);
        if (owner != null && owner != currentFile) {
          imports.put(cls.getSimpleName(), importPath(owner));
        } else {
          queue.addLast(cls);
        }
        return cls.getSimpleName();
      }

      // Collections without generics info
      if (isList(cls)) return "any[]";
      if (isMap(cls)) return "Record<string, any>";
      if (isSet(cls)) return "any[]";

      return "any";
    }

    if (type instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) type;
      Type raw = pt.getRawType();
      Type[] args = pt.getActualTypeArguments();
      if (raw instanceof Class && isList((Class<?>) raw)) {
        String elem =
            args.length == 1
                ? mapJavaTypeToTs(args[0], queue, basePackage, currentFile, partitions, imports)
                : "any";
        return elem + "[]";
      }
      if (raw instanceof Class && isSet((Class<?>) raw)) {
        String elem =
            args.length == 1
                ? mapJavaTypeToTs(args[0], queue, basePackage, currentFile, partitions, imports)
                : "any";
        return elem + "[]";
      }
      if (raw instanceof Class && isMap((Class<?>) raw)) {
        // Only support string keys; fall back to string if unknown
        String keyTs = args.length > 0 ? mapMapKeyToTs(args[0]) : "string";
        String valTs =
            args.length > 1
                ? mapJavaTypeToTs(args[1], queue, basePackage, currentFile, partitions, imports)
                : "any";
        return "Record<" + keyTs + ", " + valTs + ">";
      }
      // Fallback to any for unknown parameterized types
      return "any";
    }

    // Wildcards / type variables
    return "any";
  }

  private static boolean isNumeric(Class<?> cls) {
    return cls.equals(byte.class)
        || cls.equals(short.class)
        || cls.equals(int.class)
        || cls.equals(long.class)
        || cls.equals(float.class)
        || cls.equals(double.class)
        || cls.equals(Byte.class)
        || cls.equals(Short.class)
        || cls.equals(Integer.class)
        || cls.equals(Long.class)
        || cls.equals(Float.class)
        || cls.equals(Double.class);
  }

  private static boolean isList(Class<?> cls) {
    return java.util.List.class.isAssignableFrom(cls);
  }

  private static boolean isSet(Class<?> cls) {
    return java.util.Set.class.isAssignableFrom(cls);
  }

  private static boolean isMap(Class<?> cls) {
    return java.util.Map.class.isAssignableFrom(cls);
  }

  private static String mapMapKeyToTs(Type keyType) {
    if (keyType instanceof Class) {
      Class<?> k = (Class<?>) keyType;
      if (k.equals(String.class) || k.equals(CharSequence.class)) return "string";
      if (k.equals(Integer.class)
          || k.equals(int.class)
          || k.equals(Long.class)
          || k.equals(long.class)) return "number";
    }
    return "string";
  }

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
