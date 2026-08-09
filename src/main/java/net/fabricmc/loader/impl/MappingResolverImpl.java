//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.fabricmc.loader.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.mappingio.tree.MappingTree;

public class MappingResolverImpl implements MappingResolver {
    private final MappingTree mappings;
    private final String targetNamespace;
    private final int targetNamespaceId;

    public MappingResolverImpl(MappingTree mappings, String targetNamespace) {
        this.mappings = mappings;
        this.targetNamespace = targetNamespace;
        this.targetNamespaceId = mappings.getNamespaceId(targetNamespace);
    }

    public Collection<String> getNamespaces() {
        HashSet<String> namespaces = new HashSet(this.mappings.getDstNamespaces());
        namespaces.add(this.mappings.getSrcNamespace());
        return Collections.unmodifiableSet(namespaces);
    }

    public String getCurrentRuntimeNamespace() {
        return this.targetNamespace;
    }

    public String mapClassName(String namespace, String className) {
        if (className.indexOf(47) >= 0) {
            throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
        } else {
            return replaceSlashesWithDots(this.mappings.mapClassName(replaceDotsWithSlashes(className), this.mappings.getNamespaceId(namespace), this.targetNamespaceId));
        }
    }

    public String unmapClassName(String namespace, String className) {
        if (className.indexOf(47) >= 0) {
            throw new IllegalArgumentException("Class names must be provided in dot format: " + className);
        } else {
            return replaceSlashesWithDots(this.mappings.mapClassName(replaceDotsWithSlashes(className), this.targetNamespaceId, this.mappings.getNamespaceId(namespace)));
        }
    }

    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        if (owner.indexOf(47) >= 0) {
            throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
        } else {
            MappingTree.FieldMapping field = this.mappings.getField(replaceDotsWithSlashes(owner), name, descriptor, this.mappings.getNamespaceId(namespace));
            return field == null ? name : field.getName(this.targetNamespaceId);
        }
    }

    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        if (owner.indexOf(47) >= 0) {
            throw new IllegalArgumentException("Class names must be provided in dot format: " + owner);
        } else {
            MappingTree.MethodMapping method = this.mappings.getMethod(replaceDotsWithSlashes(owner), name, descriptor, this.mappings.getNamespaceId(namespace));
            return method == null ? name : method.getName(this.targetNamespaceId);
        }
    }

    private static String replaceSlashesWithDots(String cname) {
        return cname.replace('/', '.');
    }

    private static String replaceDotsWithSlashes(String cname) {
        return cname.replace('.', '/');
    }
}
