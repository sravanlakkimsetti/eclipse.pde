/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.ui.tests.imports;

import junit.framework.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.ui.tests.*;

public class ImportAsSourceTestCase extends PDETestCase {

	public static Test suite() {
		return new TestSuite(ImportAsSourceTestCase.class);
	}
	
	public void testPluginWithOneLibrary() {
		playScript(Catalog.IMPORT_SOURCE_1);
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			verifyProject(root.getProject("org.eclipse.core.filebuffers"));
		} catch (CoreException e) {
			fail("testPluginWithOneLibrary:" + e);
		}
	}

	public void testPluginWithMultipleLibraries() {
		playScript(Catalog.IMPORT_SOURCE_2);
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			verifyProject(root.getProject("org.eclipse.osgi"));
		} catch (CoreException e) {
			fail("testPluginWithMultipleLibraries:" + e);
		}
	}
	
	public void testMultiplePlugins() {
		playScript(Catalog.IMPORT_SOURCE_3);
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IProject[] projects = root.getProjects();
			assertTrue(projects.length > 0);
			for (int i = 0; i < projects.length; i++) {
				verifyProject(projects[i]);				
			}
		} catch (CoreException e) {
			fail("testMultiplePlugins:" + e);
		}
	}

	private void verifyProject(IProject project) throws CoreException {
		assertTrue("Project was not created.", project.exists());
		if (!project.getName().equals("org.eclipse.swt"))
			assertFalse(WorkspaceModelManager.isBinaryPluginProject(project));
		if (project.hasNature(JavaCore.NATURE_ID))
			assertTrue(checkSourceAttached(JavaCore.create(project)));
	}
		
	private boolean checkSourceAttached(IJavaProject jProject) throws CoreException {
		IPackageFragmentRoot[] roots = jProject.getPackageFragmentRoots();
		for (int i = 0; i < roots.length; i++) {
			IClasspathEntry entry = roots[i].getRawClasspathEntry();
			if (entry.getEntryKind() != IClasspathEntry.CPE_CONTAINER 
					|| !entry.getPath().equals(new Path(PDECore.CLASSPATH_CONTAINER_ID)))
				continue;
			if (roots[i].getSourceAttachmentPath() == null)
				return false;
		}
		return true;
	}
	

}
