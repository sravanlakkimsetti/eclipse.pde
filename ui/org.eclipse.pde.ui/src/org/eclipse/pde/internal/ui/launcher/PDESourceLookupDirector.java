/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceContainerType;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;
import org.eclipse.debug.core.sourcelookup.containers.ArchiveSourceContainer;
import org.eclipse.debug.core.sourcelookup.containers.ExternalArchiveSourceContainer;
import org.eclipse.debug.core.sourcelookup.containers.ProjectSourceContainer;
import org.eclipse.debug.core.sourcelookup.containers.WorkspaceSourceContainer;
import org.eclipse.debug.ui.sourcelookup.WorkingSetSourceContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.sourcelookup.containers.JavaSourceLookupParticipant;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.ModelEntry;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.PDEClasspathContainer;
import org.eclipse.pde.internal.ui.PDEPlugin;

public class PDESourceLookupDirector extends AbstractSourceLookupDirector {
	
	/**
	 * Cache of source containers by location and id (String & String)
	 */
	private Map fSourceContainerMap = new HashMap();
	
	/**
	 * Key for source container - combines two strings
	 */
	private class ContainerKey {
		private String fA;
		private String fB;
		ContainerKey(String a, String b) {
			fA = a;
			fB = b;
		}
		public boolean equals(Object obj) {
			if (obj instanceof ContainerKey) {
				ContainerKey key = (ContainerKey) obj;
				return key.fA.equals(fA) && key.fB.equals(fB);
			}
			return false;
		}
		public int hashCode() {
			return fA.hashCode() + fB.hashCode();
		}
		
	}

	private static Set fFilteredTypes;
	
	static {
		fFilteredTypes = new HashSet(3);
		fFilteredTypes.add(ProjectSourceContainer.TYPE_ID);
		fFilteredTypes.add(WorkspaceSourceContainer.TYPE_ID);
		fFilteredTypes.add(WorkingSetSourceContainer.TYPE_ID); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.sourcelookup.ISourceLookupDirector#initializeParticipants()
	 */
	public void initializeParticipants() {
		addParticipants(new ISourceLookupParticipant[] {new JavaSourceLookupParticipant()});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.sourcelookup.ISourceLookupDirector#supportsSourceContainerType(org.eclipse.debug.internal.core.sourcelookup.ISourceContainerType)
	 */
	public boolean supportsSourceContainerType(ISourceContainerType type) {
		return !fFilteredTypes.contains(type.getId());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#getSourceElement(java.lang.Object)
	 */
	public Object getSourceElement(Object element) {
		PDESourceLookupQuery query = new PDESourceLookupQuery(this, element);
		SafeRunner.run(query);
		Object result = query.getResult();
		return result != null ? result : super.getSourceElement(element);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#findSourceElements(java.lang.Object)
	 */
	public Object[] findSourceElements(Object object) throws CoreException {
		Object[] sourceElements = null;
		if (object instanceof IJavaStackFrame || object instanceof IJavaObject || object instanceof IJavaReferenceType){
			sourceElements = new Object[] {getSourceElement(object)};
		}
		if (sourceElements == null) {
			sourceElements = super.findSourceElements(object);
		}
		return sourceElements;
	}
	
	ISourceContainer[] getSourceContainers(String location, String id) throws CoreException {
		
		ContainerKey key = new ContainerKey(location, id);
		ISourceContainer[] containers = (ISourceContainer[]) fSourceContainerMap.get(key);
		if (containers != null) {
			return containers;
		}
		
		ArrayList result = new ArrayList();		
		ModelEntry entry = PluginRegistry.findEntry(id);
		
		boolean match = false;

		IPluginModelBase[] models = entry.getWorkspaceModels();		
		for (int i = 0; i < models.length; i++) {
			if (isPerfectMatch(models[i], new Path(location))) {
				IResource resource = models[i].getUnderlyingResource();
				// if the plug-in matches a workspace model,
				// add the project and any libraries not coming via a container
				// to the list of source containers, in that order
				if (resource != null) {
					addProjectSourceContainers(resource.getProject(), result);
				}
				match = true;
				break;
			}
		}

		if (!match) {
			File file = new File(location);
			if (file.isFile()) {
				// in case of linked plug-in projects that map to an external JARd plug-in,
				// use source container that maps to the library in the linked project.
				ISourceContainer container = getArchiveSourceContainer(location);
				if (container != null) {
					containers = new ISourceContainer[] {container};
					fSourceContainerMap.put(key, containers);
					return containers;
				}
			} 
			
			models = entry.getExternalModels();
			for (int i = 0; i < models.length; i++) {
				if (isPerfectMatch(models[i], new Path(location))) {
					// try all source zips found in the source code locations
					IClasspathEntry[] entries = PDEClasspathContainer.getExternalEntries(models[i]);
					for (int j = 0; j < entries.length; j++) {
						IRuntimeClasspathEntry rte = convertClasspathEntry(entries[j]);
						if (rte != null)
							result.add(rte);
					}
					break;
				}
			}
		}
		
		IRuntimeClasspathEntry[] entries = (IRuntimeClasspathEntry[])
			 				result.toArray(new IRuntimeClasspathEntry[result.size()]);
		containers = JavaRuntime.getSourceContainers(entries);
		fSourceContainerMap.put(key, containers);
		return containers;
	}
	
	private boolean isPerfectMatch(IPluginModelBase model, IPath path) {
		return model == null ? false : path.equals(new Path(model.getInstallLocation()));
	}
	
	private IRuntimeClasspathEntry convertClasspathEntry(IClasspathEntry entry) {
		if (entry == null)
			return null;
		
		IPath srcPath = entry.getSourceAttachmentPath();
		if (srcPath != null && srcPath.segmentCount() > 0) {
			IRuntimeClasspathEntry rte = 
				JavaRuntime.newArchiveRuntimeClasspathEntry(entry.getPath());
			rte.setSourceAttachmentPath(srcPath);
			rte.setSourceAttachmentRootPath(entry.getSourceAttachmentRootPath());
			return rte;
		}
		return null;
	}
	
	private ISourceContainer getArchiveSourceContainer(String location) throws JavaModelException {
		IWorkspaceRoot root = PDEPlugin.getWorkspace().getRoot();
		IFile[] containers = root.findFilesForLocation(new Path(location));
		for (int i = 0; i < containers.length; i++) {
			IJavaElement element = JavaCore.create(containers[i]);
			if (element instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot archive = (IPackageFragmentRoot)element;
				IPath path = archive.getSourceAttachmentPath();
				if (path == null || path.segmentCount() == 0)
					continue;
				
				IPath rootPath = archive.getSourceAttachmentRootPath();
				boolean detectRootPath = rootPath != null && rootPath.segmentCount() > 0;
				
				IFile archiveFile = root.getFile(path);
				if (archiveFile.exists()) 
					return new ArchiveSourceContainer(archiveFile, detectRootPath);
				
				File file = path.toFile();
				if (file.exists()) 
					return new ExternalArchiveSourceContainer(file.getAbsolutePath(), detectRootPath);		
			}
		}
		return null;
	}
	
	private void addProjectSourceContainers(IProject project, ArrayList result) throws CoreException {
		if (project == null || !project.hasNature(JavaCore.NATURE_ID))
			return;
		
		IJavaProject jProject = JavaCore.create(project);
		result.add(JavaRuntime.newProjectRuntimeClasspathEntry(jProject));
		
		IClasspathEntry[] entries = jProject.getRawClasspath();
		for (int i = 0; i < entries.length; i++) {
			IClasspathEntry entry = entries[i];
			if (entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
				IRuntimeClasspathEntry rte = convertClasspathEntry(entry);
				if (rte != null)
					result.add(rte);
			}
		}	
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector#dispose()
	 */
	public synchronized void dispose() {
		Iterator iterator = fSourceContainerMap.values().iterator();
		while (iterator.hasNext()) {
			ISourceContainer[] containers = (ISourceContainer[]) iterator.next();
			for (int i = 0; i < containers.length; i++) {
				containers[i].dispose();
			}
		}
		fSourceContainerMap.clear();
		super.dispose();
	}	
	
	
}
