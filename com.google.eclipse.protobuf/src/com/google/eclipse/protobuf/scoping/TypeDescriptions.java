/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import static java.util.Collections.emptyList;
import static org.eclipse.emf.common.util.URI.createURI;
import static org.eclipse.emf.ecore.util.EcoreUtil.getAllContents;
import static org.eclipse.xtext.resource.EObjectDescription.create;

import java.util.*;

import org.eclipse.emf.common.util.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.*;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.impl.ImportUriResolver;

import com.google.eclipse.protobuf.protobuf.*;
import com.google.eclipse.protobuf.protobuf.Package;
import com.google.eclipse.protobuf.util.*;
import com.google.inject.Inject;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
class TypeDescriptions {

  @Inject private ProtoDescriptorProvider descriptorProvider;
  @Inject private ProtobufElementFinder finder;
  @Inject private ImportedNamesProvider importedNamesProvider;
  @Inject private Imports imports;
  @Inject private LocalNamesProvider localNamesProvider;
  @Inject private PackageResolver packageResolver;
  @Inject private QualifiedNameDescriptions qualifiedNamesDescriptions;
  @Inject private ImportUriResolver uriResolver;

  <T extends Type> Collection<IEObjectDescription> localTypes(EObject root, Class<T> targetType) {
    return localTypes(root, targetType, 0);
  }

  private <T extends Type> Collection<IEObjectDescription> localTypes(EObject root, Class<T> targetType, int level) {
    List<IEObjectDescription> descriptions = new ArrayList<IEObjectDescription>();
    for (EObject element : root.eContents()) {
      if (!targetType.isInstance(element)) continue;
      List<QualifiedName> names = localNamesProvider.namesOf(element);
      int nameCount = names.size();
      for (int i = level; i < nameCount; i++) {
        descriptions.add(create(names.get(i), element));
      }
      descriptions.addAll(qualifiedNamesDescriptions.qualifiedNames(element));
      // TODO investigate if groups can have messages, and if so, add those messages to the scope.
      if (element instanceof Message) {
        descriptions.addAll(localTypes(element, targetType, level + 1));
      }
    }
    return descriptions;
  }

  <T extends Type> Collection<IEObjectDescription> importedTypes(Protobuf root, Class<T> targetType) {
    List<Import> allImports = finder.importsIn(root);
    if (allImports.isEmpty()) return emptyList();
    ResourceSet resourceSet = root.eResource().getResourceSet();
    return importedTypes(allImports, finder.packageOf(root), resourceSet, targetType);
  }

  private <T extends Type> Collection<IEObjectDescription> importedTypes(List<Import> allImports, Package aPackage,
      ResourceSet resourceSet, Class<T> targetType) {
    List<IEObjectDescription> descriptions = new ArrayList<IEObjectDescription>();
    for (Import anImport : allImports) {
      if (imports.isImportingDescriptor(anImport)) {
        descriptions.addAll(allBuiltInTypes(targetType));
        continue;
      }
      Resource importedResource = importedResource(anImport, resourceSet);
      Protobuf importedRoot = finder.rootOf(importedResource);
      if (importedRoot != null) {
        descriptions.addAll(publicImportedTypes(importedRoot, targetType));
        if (arePackagesRelated(aPackage, importedRoot)) {
          descriptions.addAll(localTypes(importedRoot, targetType));
          continue;
        }
      }
      descriptions.addAll(localTypes(importedResource, targetType));
    }
    return descriptions;
  }

  private <T extends Type> Collection<IEObjectDescription> allBuiltInTypes(Class<T> targetType) {
    List<IEObjectDescription> descriptions = new ArrayList<IEObjectDescription>();
    ProtoDescriptor descriptor = descriptorProvider.primaryDescriptor();
    for (Type t : descriptor.allTypes()) {
      if (!targetType.isInstance(t)) continue;
      T type = targetType.cast(t);
      descriptions.addAll(qualifiedNamesDescriptions.qualifiedNames(type));
    }
    return descriptions;
  }

  private Resource importedResource(Import anImport, ResourceSet resourceSet) {
    URI importUri = createURI(uriResolver.apply(anImport));
    try {
      return resourceSet.getResource(importUri, true);
    } catch (Throwable t) {
      return null;
    }
  }

  private <T extends Type> Collection<IEObjectDescription> publicImportedTypes(Protobuf root, Class<T> targetType) {
    List<Import> allImports = finder.publicImportsIn(root);
    if (allImports.isEmpty()) return emptyList();
    ResourceSet resourceSet = root.eResource().getResourceSet();
    return importedTypes(allImports, finder.packageOf(root), resourceSet, targetType);
  }

  private boolean arePackagesRelated(Package aPackage, EObject root) {
    Package p = finder.packageOf(root);
    return packageResolver.areRelated(aPackage, p);
  }

  private <T extends Type> Collection<IEObjectDescription> localTypes(Resource resource, Class<T> targetType) {
    List<IEObjectDescription> descriptions = new ArrayList<IEObjectDescription>();
    TreeIterator<Object> contents = getAllContents(resource, true);
    while (contents.hasNext()) {
      Object next = contents.next();
      if (!targetType.isInstance(next)) continue;
      T type = targetType.cast(next);
      descriptions.addAll(qualifiedNamesDescriptions.qualifiedNames(type));
      for (QualifiedName name : importedNamesProvider.namesOf(type)) {
        descriptions.add(create(name, type));
      }
    }
    return descriptions;
  }
}
