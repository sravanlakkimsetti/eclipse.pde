/*******************************************************************************
 * Copyright (c) 2005, 2021 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource Corporation - ongoing enhancements
 *******************************************************************************/
package org.eclipse.pde.internal.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateDelta;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.build.IPDEBuildConstants;
import org.eclipse.pde.internal.core.util.ManifestUtils;
import org.eclipse.pde.internal.core.util.UtilMessages;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;

public class MinimalState {

	protected State fState;

	protected long fId;

	private boolean fEEListChanged = false; // indicates that the EE has changed
	// this could be due to the system bundle changing location
	// or initially when the ee list is first created.

	private String[] fExecutionEnvironments; // an ordered list of
												// known/supported execution
												// environments

	private boolean fNoProfile;

	protected static StateObjectFactory stateObjectFactory;

	protected String fSystemBundle = IPDEBuildConstants.BUNDLE_OSGI;

	static {
		stateObjectFactory = Platform.getPlatformAdmin().getFactory();
	}

	protected MinimalState(MinimalState state) {
		this.fState = stateObjectFactory.createState(state.fState);
		this.fState.setPlatformProperties(state.fState.getPlatformProperties());
		this.fState.setResolver(Platform.getPlatformAdmin().createResolver());
		this.fId = state.fId;
		this.fEEListChanged = state.fEEListChanged;
		this.fExecutionEnvironments = state.fExecutionEnvironments;
		this.fNoProfile = state.fNoProfile;
		this.fSystemBundle = state.fSystemBundle;
	}

	protected MinimalState() {
	}

	public void addBundle(IPluginModelBase model, boolean update) {
		if (model == null) {
			return;
		}

		BundleDescription desc = model.getBundleDescription();
		long bundleId = desc == null || !update ? -1 : desc.getBundleId();
		try {
			File bundleLocation = new File(model.getInstallLocation());
			BundleDescription newDesc = addBundle(bundleLocation, bundleId,
					loadWorkspaceBundleManifest(bundleLocation, model.getUnderlyingResource()));
			model.setBundleDescription(newDesc);
			if (newDesc == null && update) {
				fState.removeBundle(desc);
			}
		} catch (CoreException e) {
			PDECore.log(e);
			model.setBundleDescription(null);
		}
	}

	@SuppressWarnings("deprecation")
	private Map<String, String> loadWorkspaceBundleManifest(File bundleLocation, IResource resource)
			throws CoreException {
		Map<String, String> manifest = ManifestUtils.loadManifest(bundleLocation);
		if (resource == null || hasDeclaredRequiredEE(manifest)) {
			return manifest;
		}

		// inject BREE based on the project's JDK, otherwise packages from all
		// JREs are eligible for dependency resolution
		// e.g. a project compiled against Java 11 may get its java.xml
		// Import-Package resolved with a Java 8 profile
		IJavaProject javaProject = JavaCore.create(resource.getProject());
		if (!javaProject.exists()) {
			return manifest;
		}
		IVMInstall projectVmInstall = JavaRuntime.getVMInstall(javaProject);

		IExecutionEnvironment executionEnvironment = Arrays
				.stream(JavaRuntime.getExecutionEnvironmentsManager().getExecutionEnvironments())
				.filter(env -> env.isStrictlyCompatible(projectVmInstall)) //
				.findFirst().orElse(null);

		if (executionEnvironment != null) {
			manifest.put(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT, executionEnvironment.getId());
		}

		return manifest;
	}

	@SuppressWarnings("deprecation")
	private boolean hasDeclaredRequiredEE(Map<String, String> manifest) {
		if (manifest.containsKey(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT)) {
			return true;
		}
		try {
			String capability = manifest.get(Constants.REQUIRE_CAPABILITY);
			ManifestElement[] header = ManifestElement.parseHeader(Constants.REQUIRE_CAPABILITY, capability);
			return header != null && Arrays.stream(header).map(ManifestElement::getValue)
					.anyMatch(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE::equals);
		} catch (BundleException e) {
			return false; // ignore
		}
	}

	public BundleDescription addBundle(Map<String, String> manifest, File bundleLocation, long bundleId)
			throws CoreException {
		try {
			// OSGi requires a dictionary over any map
			Dictionary<String, String> dictionaryManifest = FrameworkUtil.asDictionary(manifest);
			BundleDescription descriptor = stateObjectFactory.createBundleDescription(fState, dictionaryManifest,
					bundleLocation.getAbsolutePath(), bundleId == -1 ? getNextId() : bundleId);
			// new bundle
			if (bundleId == -1 || !fState.updateBundle(descriptor)) {
				fState.addBundle(descriptor);
			}
			return descriptor;
		} catch (BundleException e) {
			// A stack trace isn't helpful here, but need to list the plug-in
			// location causing the issue
			MultiStatus status = new MultiStatus(PDECore.PLUGIN_ID, 0,
					NLS.bind(UtilMessages.ErrorReadingManifest, bundleLocation.toString()), null);
			status.add(Status.error(e.getMessage()));
			throw new CoreException(status);
		} catch (IllegalArgumentException e) {
		}
		return null;
	}

	public BundleDescription addBundle(File bundleLocation, long bundleId) throws CoreException {
		Map<String, String> manifest = ManifestUtils.loadManifest(bundleLocation);
		return addBundle(bundleLocation, bundleId, manifest);
	}

	private BundleDescription addBundle(File bundleLocation, long bundleId, Map<String, String> manifest)
			throws CoreException {
		// update for development mode
		TargetWeaver.weaveManifest(manifest, bundleLocation);

		BundleDescription desc = addBundle(manifest, bundleLocation, bundleId);
		if (desc != null && manifest != null && "true".equals(manifest.get(ICoreConstants.ECLIPSE_SYSTEM_BUNDLE))) { //$NON-NLS-1$
			// if this is the system bundle then
			// indicate that the javaProfile has changed since the new system
			// bundle may not contain profiles for all EE's in the list
			fEEListChanged = true;
			fSystemBundle = desc.getSymbolicName();
		}
		if (desc != null) {
			addAuxiliaryData(desc, manifest, true);
		}
		return desc;
	}

	protected void addAuxiliaryData(BundleDescription desc, Map<String, String> manifest, boolean hasBundleStructure) {
	}

	public StateDelta resolveState(boolean incremental) {
		return internalResolveState(incremental);
	}

	/**
	 * Resolves the state incrementally based on the given bundle names.
	 *
	 * @param symbolicNames
	 * @return state delta
	 */
	public StateDelta resolveState(String[] symbolicNames) {
		if (initializePlatformProperties()) {
			return fState.resolve(false);
		}
		List<BundleDescription> bundles = new ArrayList<>();
		for (String symbolicName : symbolicNames) {
			BundleDescription[] descriptions = fState.getBundles(symbolicName);
			Collections.addAll(bundles, descriptions);
		}
		return fState.resolve(bundles.toArray(new BundleDescription[bundles.size()]));
	}

	private synchronized StateDelta internalResolveState(boolean incremental) {
		boolean fullBuildRequired = initializePlatformProperties();
		return fState.resolve(incremental && !fullBuildRequired);
	}

	protected boolean initializePlatformProperties() {
		if (fExecutionEnvironments == null && !fNoProfile) {
			setExecutionEnvironments();
		}

		if (fEEListChanged) {
			fEEListChanged = false;
			var properties = TargetPlatformHelper.getPlatformProperties(fExecutionEnvironments, this);
			return fState.setPlatformProperties(properties);
		}
		return false;
	}

	public void removeBundleDescription(BundleDescription description) {
		if (description != null) {
			fState.removeBundle(description);
		}
	}

	public void updateBundleDescription(BundleDescription description) {
		if (description != null) {
			fState.updateBundle(description);
		}
	}

	public State getState() {
		return fState;
	}

	private void setExecutionEnvironments() {
		String[] knownExecutionEnviroments = TargetPlatformHelper.getKnownExecutionEnvironments();
		if (knownExecutionEnviroments.length == 0) {
			String jreProfile = System.getProperty("pde.jreProfile"); //$NON-NLS-1$
			if (jreProfile != null && !jreProfile.isEmpty() && "none".equals(jreProfile)) { //$NON-NLS-1$
				fNoProfile = true;
			}
		}
		if (!fNoProfile) {
			fExecutionEnvironments = knownExecutionEnviroments;
		}
		fEEListChanged = true; // alway indicate the list has changed
	}

	public void addBundleDescription(BundleDescription toAdd) {
		if (toAdd != null) {
			fState.addBundle(toAdd);
		}
	}

	public long getNextId() {
		return ++fId;
	}

	public String getSystemBundle() {
		return fSystemBundle;
	}

}
