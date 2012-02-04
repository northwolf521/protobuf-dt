/*
 * Copyright (c) 2012 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor;

import static com.google.common.collect.Lists.newLinkedList;
import static org.eclipse.core.runtime.Status.*;

import org.eclipse.core.runtime.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.ui.editor.IURIEditorOpener;

import com.google.eclipse.protobuf.resource.*;
import com.google.inject.Inject;

/**
 * Navigates to the definition of a model object, opening necessary files if necessary.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ModelObjectDefinitionNavigator {
  @Inject private IndexLookup lookup;
  @Inject private IURIEditorOpener editorOpener;
  @Inject private ResourceDescriptions resources;

  /**
   * Navigates to the definition of the model object whose qualified name matches any of the given ones. This method
   * will open the file containing the model object definition if necessary.
   * @param query information needed to find the object model to navigate to.
   * @return the result of the operation.
    */
  public IStatus navigateToDefinition(Query query) {
    IResourceDescription resource = lookup.resourceIn(query.filePath);
    if (resource == null) {
      return CANCEL_STATUS;
    }
    for (QualifiedName qualifiedName : query.qualifiedNames) {
      URI uri = resources.modelObjectUri(resource, qualifiedName);
      if (uri != null) {
        editorOpener.open(uri, true);
        return OK_STATUS;
      }
    }
    return CANCEL_STATUS;
  }

  /**
   * Information needed to find the object model to navigate to.
   *
   * @author alruiz@google.com (Alex Ruiz)
   */
  public static class Query {
    final Iterable<QualifiedName> qualifiedNames;
    final IPath filePath;

    /**
     * Creates a new <code>{@link Query}</code>, to be used by
     * <code>{@link ModelObjectDefinitionNavigator#navigateToDefinition(Query)}</code>.
     * @param qualifiedNames all the possible qualified names the model object to look for may have.
     * @param filePath the path and name of the file where to perform the lookup.
     * @return the created {@code Query}.
     */
    public static Query newQuery(Iterable<QualifiedName> qualifiedNames, IPath filePath) {
      return new Query(qualifiedNames, filePath);
    }

    private Query(Iterable<QualifiedName> qualifiedNames, IPath filePath) {
      this.qualifiedNames = newLinkedList(qualifiedNames);
      this.filePath = filePath;
    }

    @Override public String toString() {
      String format = "%s[qualifiedNames=%s, filePath=%s]";
      return String.format(format, getClass().getSimpleName(), qualifiedNames, filePath);
    }
  }
}