/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Francis Upton - <francisu@ieee.org> - 
 *     		Fix for Bug 217777 [Workbench] Workbench event loop does not terminate if Display is closed
 *******************************************************************************/

package org.eclipse.ui.internal;

import com.ibm.icu.util.ULocale;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.CommandManager;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.common.EventManager;
import org.eclipse.core.commands.contexts.ContextManager;
import org.eclipse.core.commands.contexts.ContextManagerEvent;
import org.eclipse.core.commands.contexts.IContextManagerListener;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionDelta;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IPlatformRunnable;
import org.eclipse.core.runtime.IProduct;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.contexts.ContextFunction;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IContextFunction;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.InjectionException;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.e4.ui.internal.workbench.swt.E4Application;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.commands.MBindingTable;
import org.eclipse.e4.ui.model.application.commands.MCategory;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.commands.impl.CommandsFactoryImpl;
import org.eclipse.e4.ui.model.application.descriptor.basic.MPartDescriptor;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.model.application.ui.basic.impl.BasicFactoryImpl;
import org.eclipse.e4.ui.services.EContextService;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ExternalActionManager;
import org.eclipse.jface.action.ExternalActionManager.CommandCallback;
import org.eclipse.jface.action.ExternalActionManager.IActiveChecker;
import org.eclipse.jface.action.ExternalActionManager.IExecuteApplicable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.bindings.BindingManager;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.ModalContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.service.runnable.StartupMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.DeviceData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IDecoratorManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.ILocalWorkingSetManager;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.ISaveableFilter;
import org.eclipse.ui.ISaveablesLifecycleListener;
import org.eclipse.ui.ISaveablesSource;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.ISourceProvider;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.Saveable;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.activities.IWorkbenchActivitySupport;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.commands.ICommandImageService;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IWorkbenchCommandSupport;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.contexts.IWorkbenchContextSupport;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.help.IWorkbenchHelpSystem;
import org.eclipse.ui.internal.StartupThreading.StartupRunnable;
import org.eclipse.ui.internal.actions.CommandAction;
import org.eclipse.ui.internal.activities.ws.WorkbenchActivitySupport;
import org.eclipse.ui.internal.browser.WorkbenchBrowserSupport;
import org.eclipse.ui.internal.commands.CommandImageManager;
import org.eclipse.ui.internal.commands.CommandImageService;
import org.eclipse.ui.internal.commands.CommandService;
import org.eclipse.ui.internal.commands.WorkbenchCommandSupport;
import org.eclipse.ui.internal.contexts.ActiveContextSourceProvider;
import org.eclipse.ui.internal.contexts.ContextService;
import org.eclipse.ui.internal.contexts.WorkbenchContextSupport;
import org.eclipse.ui.internal.dialogs.PropertyPageContributorManager;
import org.eclipse.ui.internal.e4.compatibility.CompatibilityEditor;
import org.eclipse.ui.internal.e4.compatibility.CompatibilityPart;
import org.eclipse.ui.internal.e4.compatibility.E4Util;
import org.eclipse.ui.internal.handlers.LegacyHandlerService;
import org.eclipse.ui.internal.help.WorkbenchHelpSystem;
import org.eclipse.ui.internal.intro.IntroDescriptor;
import org.eclipse.ui.internal.keys.BindingService;
import org.eclipse.ui.internal.menus.FocusControlSourceProvider;
import org.eclipse.ui.internal.menus.WorkbenchMenuService;
import org.eclipse.ui.internal.misc.Policy;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.internal.misc.UIStats;
import org.eclipse.ui.internal.model.ContributionService;
import org.eclipse.ui.internal.progress.ProgressManager;
import org.eclipse.ui.internal.progress.ProgressManagerUtil;
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants;
import org.eclipse.ui.internal.registry.UIExtensionTracker;
import org.eclipse.ui.internal.registry.ViewDescriptor;
import org.eclipse.ui.internal.services.EvaluationService;
import org.eclipse.ui.internal.services.IServiceLocatorCreator;
import org.eclipse.ui.internal.services.IWorkbenchLocationService;
import org.eclipse.ui.internal.services.MenuSourceProvider;
import org.eclipse.ui.internal.services.ServiceLocator;
import org.eclipse.ui.internal.services.ServiceLocatorCreator;
import org.eclipse.ui.internal.services.SourceProviderService;
import org.eclipse.ui.internal.services.WorkbenchLocationService;
import org.eclipse.ui.internal.splash.EclipseSplashHandler;
import org.eclipse.ui.internal.splash.SplashHandlerFactory;
import org.eclipse.ui.internal.testing.WorkbenchTestable;
import org.eclipse.ui.internal.themes.ColorDefinition;
import org.eclipse.ui.internal.themes.FontDefinition;
import org.eclipse.ui.internal.themes.ThemeElementHelper;
import org.eclipse.ui.internal.themes.WorkbenchThemeManager;
import org.eclipse.ui.internal.tweaklets.GrabFocus;
import org.eclipse.ui.internal.tweaklets.Tweaklets;
import org.eclipse.ui.internal.util.PrefUtil;
import org.eclipse.ui.internal.util.Util;
import org.eclipse.ui.intro.IIntroManager;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.menus.IMenuService;
import org.eclipse.ui.model.IContributionService;
import org.eclipse.ui.operations.IWorkbenchOperationSupport;
import org.eclipse.ui.progress.IProgressService;
import org.eclipse.ui.services.IDisposable;
import org.eclipse.ui.services.IEvaluationService;
import org.eclipse.ui.services.IServiceScopes;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.splash.AbstractSplashHandler;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.ui.swt.IFocusService;
import org.eclipse.ui.themes.IThemeManager;
import org.eclipse.ui.views.IViewDescriptor;
import org.eclipse.ui.views.IViewRegistry;
import org.eclipse.ui.wizards.IWizardRegistry;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The workbench class represents the top of the Eclipse user interface. Its
 * primary responsability is the management of workbench windows, dialogs,
 * wizards, and other workbench-related windows.
 * <p>
 * Note that any code that is run during the creation of a workbench instance
 * should not required access to the display.
 * </p>
 * <p>
 * Note that this internal class changed significantly between 2.1 and 3.0.
 * Applications that used to define subclasses of this internal class need to be
 * rewritten to use the new workbench advisor API.
 * </p>
 */
public final class Workbench extends EventManager implements IWorkbench {

	private final class StartupProgressBundleListener implements SynchronousBundleListener {

		private final IProgressMonitor progressMonitor;

		private final int maximumProgressCount;

		// stack of names of bundles currently starting
		private final List starting;

		StartupProgressBundleListener(IProgressMonitor progressMonitor, int maximumProgressCount) {
			super();
			this.progressMonitor = progressMonitor;
			this.maximumProgressCount = maximumProgressCount;
			this.starting = new ArrayList();
		}

		public void bundleChanged(BundleEvent event) {
			int eventType = event.getType();
			String bundleName;

			synchronized (this) {
				if (eventType == BundleEvent.STARTING) {
					starting.add(bundleName = event.getBundle().getSymbolicName());
				} else if (eventType == BundleEvent.STARTED) {
					progressCount++;
					if (progressCount <= maximumProgressCount) {
						progressMonitor.worked(1);
					}
					int index = starting.lastIndexOf(event.getBundle().getSymbolicName());
					if (index >= 0) {
						starting.remove(index);
					}
					if (index != starting.size()) {
						return; // not currently displayed
					}
					bundleName = index == 0 ? null : (String) starting.get(index - 1);
				} else {
					return; // uninteresting event
				}
			}

			String taskName;

			if (bundleName == null) {
				taskName = WorkbenchMessages.Startup_Loading_Workbench;
			} else {
				taskName = NLS.bind(WorkbenchMessages.Startup_Loading, bundleName);
			}

			progressMonitor.subTask(taskName);
		}
	}

	/**
	 * Family for the early startup job.
	 */
	public static final String EARLY_STARTUP_FAMILY = "earlyStartup"; //$NON-NLS-1$

	static final String VERSION_STRING[] = { "0.046", "2.0" }; //$NON-NLS-1$ //$NON-NLS-2$

	static final String DEFAULT_WORKBENCH_STATE_FILENAME = "workbench.xml"; //$NON-NLS-1$

	/**
	 * Holds onto the only instance of Workbench.
	 */
	private static Workbench instance;

	/**
	 * The testable object facade.
	 * 
	 * @since 3.0
	 */
	private static WorkbenchTestable testableObject;

	/**
	 * Signals that the workbench should create a splash implementation when
	 * instantiated. Intial value is <code>true</code>.
	 * 
	 * @since 3.3
	 */
	private static boolean createSplash = true;

	/**
	 * The splash handler.
	 */
	private static AbstractSplashHandler splash;

	/**
	 * The display used for all UI interactions with this workbench.
	 * 
	 * @since 3.0
	 */
	private Display display;


	private WorkbenchWindow activatedWindow;

	private EditorHistory editorHistory;

	private boolean runEventLoop = true;

	private boolean isStarting = true;

	private boolean isClosing = false;

	/**
	 * PlatformUI return code (as opposed to IPlatformRunnable return code).
	 */
	private int returnCode = PlatformUI.RETURN_UNSTARTABLE;

	/**
	 * Advisor providing application-specific configuration and customization of
	 * the workbench.
	 * 
	 * @since 3.0
	 */
	private WorkbenchAdvisor advisor;

	/**
	 * Object for configuring the workbench. Lazily initialized to an instance
	 * unique to the workbench instance.
	 * 
	 * @since 3.0
	 */
	private WorkbenchConfigurer workbenchConfigurer;

	// for dynamic UI
	/**
	 * ExtensionEventHandler handles extension life-cycle events.
	 */
	private ExtensionEventHandler extensionEventHandler;

	/**
	 * A count of how many large updates are going on. This tracks nesting of
	 * requests to disable services during a large update -- similar to the
	 * <code>setRedraw</code> functionality on <code>Control</code>. When this
	 * value becomes greater than zero, services are disabled. When this value
	 * becomes zero, services are enabled. Please see
	 * <code>largeUpdateStart()</code> and <code>largeUpdateEnd()</code>.
	 */
	private int largeUpdates = 0;

	/**
	 * The service locator maintained by the workbench. These services are
	 * initialized during workbench during the <code>init</code> method.
	 */
	private final ServiceLocator serviceLocator;

	/**
	 * A count of how many plug-ins were loaded while restoring the workbench
	 * state. Initially -1 for unknown number.
	 */
	private int progressCount = -1;

	/**
	 * Listener list for registered IWorkbenchListeners .
	 */
	private ListenerList workbenchListeners = new ListenerList(ListenerList.IDENTITY);

	private ServiceRegistration workbenchService;

	private MApplication application;

	private IEclipseContext e4Context;

	private IEventBroker eventBroker;

	boolean initializationDone = false;

	/**
	 * Creates a new workbench.
	 * 
	 * @param display
	 *            the display to be used for all UI interactions with the
	 *            workbench
	 * @param advisor
	 *            the application-specific advisor that configures and
	 *            specializes this workbench instance
	 * @since 3.0
	 */
	private Workbench(Display display, WorkbenchAdvisor advisor, MApplication app,
			IEclipseContext appContext) {
		super();
		StartupThreading.setWorkbench(this);
		if (instance != null && instance.isRunning()) {
			throw new IllegalStateException(WorkbenchMessages.Workbench_CreatingWorkbenchTwice);
		}
		Assert.isNotNull(display);
		Assert.isNotNull(advisor);
		this.advisor = advisor;
		this.display = display;
		application = app;
		e4Context = appContext;
		Workbench.instance = this;
		eventBroker = (IEventBroker) e4Context.get(IEventBroker.class.getName());

		appContext.set(getClass().getName(), this);
		appContext.set(IWorkbench.class.getName(), this);

		// for dynamic UI [This seems to be for everything that isn't handled by
		// some
		// subclass of RegistryManager. I think that when an extension is moved
		// to the
		// RegistryManager implementation, then it should be removed from the
		// list in
		// ExtensionEventHandler#appear.
		// I've found that the new wizard extension in particular is a poor
		// choice to
		// use as an example, since the result of reading the registry is not
		// cached
		// -- so it is re-read each time. The only real contribution of this
		// dialog is
		// to show the user a nice dialog describing the addition.]
		extensionEventHandler = new ExtensionEventHandler(this);
		Platform.getExtensionRegistry().addRegistryChangeListener(extensionEventHandler);
		IServiceLocatorCreator slc = new ServiceLocatorCreator();
		serviceLocator = (ServiceLocator) slc.createServiceLocator(null, null, new IDisposable() {
			public void dispose() {
				final Display display = getDisplay();
				if (display != null && !display.isDisposed()) {
					MessageDialog.openInformation(null,
							WorkbenchMessages.Workbench_NeedsClose_Title,
							WorkbenchMessages.Workbench_NeedsClose_Message);
					close(PlatformUI.RETURN_RESTART, true);
				}
			}
		});
		serviceLocator.setContext(appContext);
		serviceLocator.registerService(IServiceLocatorCreator.class, slc);
		serviceLocator.registerService(IWorkbenchLocationService.class,
				new WorkbenchLocationService(IServiceScopes.WORKBENCH_SCOPE, this, null, null,
						null, null, 0));

	}

	/**
	 * Returns the one and only instance of the workbench, if there is one.
	 * 
	 * @return the workbench, or <code>null</code> if the workbench has not been
	 *         created, or has been created and already completed
	 */
	public static final Workbench getInstance() {
		return instance;
	}

	/**
	 * Creates the workbench and associates it with the the given display and
	 * workbench advisor, and runs the workbench UI. This entails processing and
	 * dispatching events until the workbench is closed or restarted.
	 * <p>
	 * This method is intended to be called by <code>PlatformUI</code>. Fails if
	 * the workbench UI has already been created.
	 * </p>
	 * <p>
	 * The display passed in must be the default display.
	 * </p>
	 * 
	 * @param display
	 *            the display to be used for all UI interactions with the
	 *            workbench
	 * @param advisor
	 *            the application-specific advisor that configures and
	 *            specializes the workbench
	 * @return return code {@link PlatformUI#RETURN_OK RETURN_OK}for normal
	 *         exit; {@link PlatformUI#RETURN_RESTART RETURN_RESTART}if the
	 *         workbench was terminated with a call to
	 *         {@link IWorkbench#restart IWorkbench.restart}; other values
	 *         reserved for future use
	 */
	public static final int createAndRunWorkbench(final Display display,
			final WorkbenchAdvisor advisor) {
		final int[] returnCode = new int[1];
		Realm.runWithDefault(SWTObservables.getRealm(display), new Runnable() {
			public void run() {
				ULocale.setDefault(new ULocale(Platform.getNL() + Platform.getNLExtensions()));

				Object obj = getApplication(Platform.getCommandLineArgs());
				if (obj instanceof E4Application) {
					E4Application e4app = (E4Application) obj;
					E4Workbench e4Workbench = e4app.createE4Workbench(getApplicationContext(),
							display);
					IEclipseContext workbenchContext = e4Workbench.getContext();
					workbenchContext.set(Display.class, display);

					// create the workbench instance
					Workbench workbench = new Workbench(display, advisor, e4Workbench
							.getApplication(), e4Workbench.getContext());
					// run the legacy workbench once
					returnCode[0] = workbench.runUI();
					// run the e4 event loop and instantiate ... well, stuff
					e4Workbench.createAndRunUI(e4Workbench.getApplication());
					WorkbenchMenuService wms = (WorkbenchMenuService) e4Workbench.getContext().get(
							IMenuService.class);
					wms.dispose();
					e4app.saveModel();
					e4Workbench.close();
					returnCode[0] = workbench.returnCode;
				}
			}
		});
		return returnCode[0];
	}

	private static ServiceTracker instanceAppContext;

	static IApplicationContext getApplicationContext() {
		if (instanceAppContext == null) {
			instanceAppContext = new ServiceTracker(
					WorkbenchPlugin.getDefault().getBundleContext(), IApplicationContext.class
							.getName(), null);
			instanceAppContext.open();
		}
		return (IApplicationContext) instanceAppContext.getService();
	}

	static Object getApplication(String[] args) {
		// Find the name of the application as specified by the PDE JUnit
		// launcher.
		// If no application is specified, the 3.0 default workbench application
		// is returned.
		IExtension extension = Platform.getExtensionRegistry().getExtension(Platform.PI_RUNTIME,
				Platform.PT_APPLICATIONS, "org.eclipse.e4.ui.workbench.swt.E4Application"); //$NON-NLS-1$

		Assert.isNotNull(extension);

		// If the extension does not have the correct grammar, return null.
		// Otherwise, return the application object.
		try {
			IConfigurationElement[] elements = extension.getConfigurationElements();
			if (elements.length > 0) {
				IConfigurationElement[] runs = elements[0].getChildren("run"); //$NON-NLS-1$
				if (runs.length > 0) {
					Object runnable;
					runnable = runs[0].createExecutableExtension("class");//$NON-NLS-1$
					if (runnable instanceof IPlatformRunnable || runnable instanceof IApplication)
						return runnable;
				}
			}
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Creates the <code>Display</code> to be used by the workbench.
	 * 
	 * @return the display
	 */
	public static Display createDisplay() {
		// setup the application name used by SWT to lookup resources on some
		// platforms
		String applicationName = WorkbenchPlugin.getDefault().getAppName();
		if (applicationName != null) {
			Display.setAppName(applicationName);
		}

		// create the display
		Display newDisplay = Display.getCurrent();
		if (newDisplay == null) {
			if (Policy.DEBUG_SWT_GRAPHICS || Policy.DEBUG_SWT_DEBUG) {
				DeviceData data = new DeviceData();
				if (Policy.DEBUG_SWT_GRAPHICS) {
					data.tracking = true;
				}
				if (Policy.DEBUG_SWT_DEBUG) {
					data.debug = true;
				}
				newDisplay = new Display(data);
			} else {
				newDisplay = new Display();
			}
		}

		// workaround for 1GEZ9UR and 1GF07HN
		newDisplay.setWarnings(false);

		// Set the priority higher than normal so as to be higher
		// than the JobManager.
		Thread.currentThread().setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));

		initializeImages();

		return newDisplay;
	}

	/**
	 * Create the splash wrapper and set it to work.
	 * 
	 * @since 3.3
	 */
	private void createSplashWrapper() {
		final Display display = getDisplay();
		String splashLoc = System.getProperty("org.eclipse.equinox.launcher.splash.location"); //$NON-NLS-1$
		final Image background = loadImage(splashLoc);

		SafeRunnable run = new SafeRunnable() {

			public void run() throws Exception {
				if (!WorkbenchPlugin.isSplashHandleSpecified()) {
					createSplash = false;
					return;
				}

				// create the splash
				getSplash();
				if (splash == null) {
					createSplash = false;
					return;
				}

				Shell splashShell = splash.getSplash();
				if (splashShell == null) {
					splashShell = WorkbenchPlugin.getSplashShell(display);

					if (splashShell == null)
						return;
					if (background != null)
						splashShell.setBackgroundImage(background);
				}

				Dictionary properties = new Hashtable();
				properties.put(Constants.SERVICE_RANKING, new Integer(Integer.MAX_VALUE));
				BundleContext context = WorkbenchPlugin.getDefault().getBundleContext();
				final ServiceRegistration registration[] = new ServiceRegistration[1];
				StartupMonitor startupMonitor = new StartupMonitor() {

					public void applicationRunning() {
						splash.dispose();
						if (background != null)
							background.dispose();
						registration[0].unregister(); // unregister ourself
						WorkbenchPlugin.unsetSplashShell(display);
					}

					public void update() {
						// do nothing - we come into the picture far too late
						// for this to be relevant
					}
				};
				registration[0] = context.registerService(StartupMonitor.class.getName(),
						startupMonitor, properties);

				splash.init(splashShell);
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * org.eclipse.jface.util.SafeRunnable#handleException(java.lang
			 * .Throwable)
			 */
			public void handleException(Throwable e) {
				StatusManager.getManager().handle(
						StatusUtil.newStatus(WorkbenchPlugin.PI_WORKBENCH,
								"Could not instantiate splash", e)); //$NON-NLS-1$
				createSplash = false;
				splash = null;
				if (background != null)
					background.dispose();

			}
		};
		SafeRunner.run(run);
	}

	/**
	 * Load an image from a filesystem path.
	 * 
	 * @param splashLoc
	 *            the location to load from
	 * @return the image or <code>null</code>
	 * @since 3.3
	 */
	private Image loadImage(String splashLoc) {
		Image background = null;
		if (splashLoc != null) {
			InputStream input = null;
			try {
				input = new BufferedInputStream(new FileInputStream(splashLoc));
				background = new Image(display, input);
			} catch (SWTException e) {
				StatusManager.getManager().handle(
						StatusUtil.newStatus(WorkbenchPlugin.PI_WORKBENCH, e));
			} catch (IOException e) {
				StatusManager.getManager().handle(
						StatusUtil.newStatus(WorkbenchPlugin.PI_WORKBENCH, e));
			} finally {
				if (input != null) {
					try {
						input.close();
					} catch (IOException e) {
						// he's done for
					}
				}
			}
		}
		return background;
	}

	/**
	 * Return the splash handler for this application. If none is specifically
	 * provided the default Eclipse implementation is returned.
	 * 
	 * @return the splash handler for this application or <code>null</code>
	 * @since 3.3
	 */
	private static AbstractSplashHandler getSplash() {
		if (!createSplash)
			return null;

		if (splash == null) {

			IProduct product = Platform.getProduct();
			if (product != null)
				splash = SplashHandlerFactory.findSplashHandlerFor(product);

			if (splash == null)
				splash = new EclipseSplashHandler();
		}
		return splash;
	}

	/**
	 * Returns the testable object facade, for use by the test harness.
	 * 
	 * @return the testable object facade
	 * @since 3.0
	 */
	public static WorkbenchTestable getWorkbenchTestable() {
		if (testableObject == null) {
			testableObject = new WorkbenchTestable();
		}
		return testableObject;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 * 
	 * @since 3.2
	 */
	public void addWorkbenchListener(IWorkbenchListener listener) {
		workbenchListeners.add(listener);
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 * 
	 * @since 3.2
	 */
	public void removeWorkbenchListener(IWorkbenchListener listener) {
		workbenchListeners.remove(listener);
	}

	/**
	 * Fire workbench preShutdown event, stopping at the first one to veto
	 * 
	 * @param forced
	 *            flag indicating whether the shutdown is being forced
	 * @return <code>true</code> to allow the workbench to proceed with
	 *         shutdown, <code>false</code> to veto a non-forced shutdown
	 * @since 3.2
	 */
	boolean firePreShutdown(final boolean forced) {
		Object list[] = workbenchListeners.getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWorkbenchListener l = (IWorkbenchListener) list[i];
			final boolean[] result = new boolean[] { false };
			SafeRunnable.run(new SafeRunnable() {
				public void run() {
					result[0] = l.preShutdown(Workbench.this, forced);
				}
			});
			if (!result[0]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Fire workbench postShutdown event.
	 * 
	 * @since 3.2
	 */
	void firePostShutdown() {
		Object list[] = workbenchListeners.getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWorkbenchListener l = (IWorkbenchListener) list[i];
			SafeRunnable.run(new SafeRunnable() {
				public void run() {
					l.postShutdown(Workbench.this);
				}
			});
		}
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public void addWindowListener(IWindowListener l) {
		addListenerObject(l);
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public void removeWindowListener(IWindowListener l) {
		removeListenerObject(l);
	}

	/**
	 * Fire window opened event.
	 * 
	 * @param window
	 *            The window which just opened; should not be <code>null</code>.
	 */
	protected void fireWindowOpened(final IWorkbenchWindow window) {
		Object list[] = getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			SafeRunner.run(new SafeRunnable() {
				public void run() {
					l.windowOpened(window);
				}
			});
		}
	}

	/**
	 * Fire window closed event.
	 * 
	 * @param window
	 *            The window which just closed; should not be <code>null</code>.
	 */
	protected void fireWindowClosed(final IWorkbenchWindow window) {
		if (activatedWindow == window) {
			// Do not hang onto it so it can be GC'ed
			activatedWindow = null;
		}

		Object list[] = getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			SafeRunner.run(new SafeRunnable() {
				public void run() {
					l.windowClosed(window);
				}
			});
		}
	}

	/**
	 * Fire window activated event.
	 * 
	 * @param window
	 *            The window which was just activated; should not be
	 *            <code>null</code>.
	 */
	protected void fireWindowActivated(final IWorkbenchWindow window) {
		Object list[] = getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			SafeRunner.run(new SafeRunnable() {
				public void run() {
					l.windowActivated(window);
				}
			});
		}
	}

	/**
	 * Fire window deactivated event.
	 * 
	 * @param window
	 *            The window which was just deactivated; should not be
	 *            <code>null</code>.
	 */
	protected void fireWindowDeactivated(final IWorkbenchWindow window) {
		Object list[] = getListeners();
		for (int i = 0; i < list.length; i++) {
			final IWindowListener l = (IWindowListener) list[i];
			SafeRunner.run(new SafeRunnable() {
				public void run() {
					l.windowDeactivated(window);
				}
			});
		}
	}

	/**
	 * Closes the workbench. Assumes that the busy cursor is active.
	 * 
	 * @param force
	 *            true if the close is mandatory, and false if the close is
	 *            allowed to fail
	 * @return true if the close succeeded, and false otherwise
	 */
	private boolean busyClose(final boolean force) {

		// notify the advisor of preShutdown and allow it to veto if not forced
		isClosing = advisor.preShutdown();
		if (!force && !isClosing) {
			return false;
		}

		// notify regular workbench clients of preShutdown and allow them to
		// veto if not forced
		isClosing = firePreShutdown(force);
		if (!force && !isClosing) {
			return false;
		}

		// save any open editors if they are dirty
		isClosing = saveAllEditors(!force, true);
		if (!force && !isClosing) {
			return false;
		}

		boolean closeEditors = !force
				&& PrefUtil.getAPIPreferenceStore().getBoolean(
						IWorkbenchPreferenceConstants.CLOSE_EDITORS_ON_EXIT);
		if (closeEditors) {
			SafeRunner.run(new SafeRunnable() {
				public void run() {
					IWorkbenchWindow windows[] = getWorkbenchWindows();
					for (int i = 0; i < windows.length; i++) {
						IWorkbenchPage pages[] = windows[i].getPages();
						for (int j = 0; j < pages.length; j++) {
							isClosing = isClosing && pages[j].closeAllEditors(false);
						}
					}
				}
			});
			if (!force && !isClosing) {
				return false;
			}
		}

		// discard editors that with non-ppersistable inputs
		SafeRunner.run(new SafeRunnable() {
			public void run() {
				IWorkbenchWindow windows[] = getWorkbenchWindows();
				for (int i = 0; i < windows.length; i++) {
					IWorkbenchPage pages[] = windows[i].getPages();
					for (int j = 0; j < pages.length; j++) {
						List<EditorReference> editorReferences = ((WorkbenchPage) pages[j])
								.getInternalEditorReferences();
						List<EditorReference> referencesToClose = new ArrayList<EditorReference>();
						for (EditorReference reference : editorReferences) {
							IEditorPart editor = reference.getEditor(false);
							if (editor != null && !reference.persist()) {
								referencesToClose.add(reference);
							}
						}
						
						for (EditorReference reference : referencesToClose) {
							((WorkbenchPage) pages[j]).closeEditor(reference);
						}
					}
				}
			}
		});

		// persist view states
		SafeRunner.run(new SafeRunnable() {
			public void run() {
				IWorkbenchWindow windows[] = getWorkbenchWindows();
				for (int i = 0; i < windows.length; i++) {
					IWorkbenchPage pages[] = windows[i].getPages();
					for (int j = 0; j < pages.length; j++) {
						IViewReference[] references = pages[j].getViewReferences();
						for (int k = 0; k < references.length; k++) {
							if (references[k].getView(false) != null) {
								((ViewReference) references[k]).persist();
							}
						}
					}
				}
			}
		});

		if (!force && !isClosing) {
			return false;
		}

		SafeRunner.run(new SafeRunnable(WorkbenchMessages.ErrorClosing) {
			public void run() {
				if (isClosing || force) {
					// isClosing = windowManager.close();
					E4Util.unsupported("Need to close since no windowManager"); //$NON-NLS-1$
					for (IWorkbenchWindow window : getWorkbenchWindows()) {
						((WorkbenchWindow) window).close(false);
					}
				}
			}
		});

		if (!force && !isClosing) {
			return false;
		}

		shutdown();

		IPresentationEngine engine = application.getContext().get(IPresentationEngine.class);
		engine.stop();
		//System.err.println("stop()"); //$NON-NLS-1$

		runEventLoop = false;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#saveAllEditors(boolean)
	 */
	public boolean saveAllEditors(boolean confirm) {
		return saveAllEditors(confirm, false);
	}

	private boolean saveAllEditors(boolean confirm, boolean closing) {
		for (IWorkbenchWindow window : getWorkbenchWindows()) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				if (!((WorkbenchPage) page).saveAllEditors(confirm, closing)) {
					return false;
				}
			}
		}
		return true;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public boolean close() {
		return close(PlatformUI.RETURN_OK, false);
	}

	/**
	 * Closes the workbench, returning the given return code from the run
	 * method. If forced, the workbench is closed no matter what.
	 * 
	 * @param returnCode
	 *            {@link PlatformUI#RETURN_OK RETURN_OK}for normal exit;
	 *            {@link PlatformUI#RETURN_RESTART RETURN_RESTART}if the
	 *            workbench was terminated with a call to
	 *            {@link IWorkbench#restart IWorkbench.restart};
	 *            {@link PlatformUI#RETURN_EMERGENCY_CLOSE} for an emergency
	 *            shutdown {@link PlatformUI#RETURN_UNSTARTABLE
	 *            RETURN_UNSTARTABLE}if the workbench could not be started;
	 *            other values reserved for future use
	 * 
	 * @param force
	 *            true to force the workbench close, and false for a "soft"
	 *            close that can be canceled
	 * @return true if the close was successful, and false if the close was
	 *         canceled
	 */
	/* package */
	boolean close(int returnCode, final boolean force) {
		this.returnCode = returnCode;
		final boolean[] ret = new boolean[1];
		BusyIndicator.showWhile(null, new Runnable() {
			public void run() {
				ret[0] = busyClose(force);
			}
		});
		return ret[0];
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow getActiveWorkbenchWindow() {
		// Return null if called from a non-UI thread.
		// This is not spec'ed behaviour and is misleading, however this is how
		// it
		// worked in 2.1 and we cannot change it now.
		// For more details, see [Bug 57384] [RCP] Main window not active on
		// startup
		if (Display.getCurrent() == null || !initializationDone) {
			return null;
		}

		// the source providers try to update again during shutdown
		if (isClosing) {
			return null;
		}

		MWindow activeWindow = application.getSelectedElement();
		if (activeWindow == null && !application.getChildren().isEmpty()) {
			activeWindow = application.getChildren().get(0);
		}

		return createWorkbenchWindow(getDefaultPageInput(), getPerspectiveRegistry()
				.findPerspectiveWithId(getDefaultPerspectiveId()),
				activeWindow, false);
	}

	IWorkbenchWindow createWorkbenchWindow(IAdaptable input, IPerspectiveDescriptor descriptor,
			MWindow window, boolean newWindow) {
		IEclipseContext windowContext = window.getContext();
		if (windowContext == null) {
			windowContext = E4Workbench.initializeContext(
					e4Context, window);
			E4Workbench.processHierarchy(window);
		}
		WorkbenchWindow result = (WorkbenchWindow) windowContext.get(IWorkbenchWindow.class
				.getName());
		if (result == null) {
			result = new WorkbenchWindow(input, descriptor);

			if (newWindow) {
				Point size = result.getWindowConfigurer().getInitialSize();
				window.setWidth(size.x);
				window.setHeight(size.y);
				application.getChildren().add(window);
				application.setSelectedElement(window);
			}

			ContextInjectionFactory.inject(result, windowContext);
			windowContext.set(IWorkbenchWindow.class.getName(), result);

			if (application.getSelectedElement() == window) {
				application.getContext().set(ISources.ACTIVE_WORKBENCH_WINDOW_NAME, result);
				application.getContext().set(ISources.ACTIVE_WORKBENCH_WINDOW_SHELL_NAME, result.getShell());
			}
		}
		return result;
	}

	/*
	 * Returns the editor history.
	 */
	public EditorHistory getEditorHistory() {
		if (editorHistory == null) {
			editorHistory = new EditorHistory();
		}
		return editorHistory;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IEditorRegistry getEditorRegistry() {
		return WorkbenchPlugin.getDefault().getEditorRegistry();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchOperationSupport getOperationSupport() {
		return WorkbenchPlugin.getDefault().getOperationSupport();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IPerspectiveRegistry getPerspectiveRegistry() {
		return WorkbenchPlugin.getDefault().getPerspectiveRegistry();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public PreferenceManager getPreferenceManager() {
		return WorkbenchPlugin.getDefault().getPreferenceManager();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IPreferenceStore getPreferenceStore() {
		return WorkbenchPlugin.getDefault().getPreferenceStore();
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public ISharedImages getSharedImages() {
		return WorkbenchPlugin.getDefault().getSharedImages();
	}



	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public int getWorkbenchWindowCount() {
		return getWorkbenchWindows().length;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow[] getWorkbenchWindows() {
		List<IWorkbenchWindow> windows = new ArrayList<IWorkbenchWindow>();
		for (MWindow window : application.getChildren()) {
			IEclipseContext context = window.getContext();
			if (context != null) {
				IWorkbenchWindow wwindow = (IWorkbenchWindow) context.get(IWorkbenchWindow.class
						.getName());
				if (wwindow != null) {
					windows.add(wwindow);
				}
			}
		}
		return windows.toArray(new IWorkbenchWindow[windows.size()]);
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkingSetManager getWorkingSetManager() {
		return WorkbenchPlugin.getDefault().getWorkingSetManager();
	}

	/**
	 * {@inheritDoc}
	 */
	public ILocalWorkingSetManager createLocalWorkingSetManager() {
		return new LocalWorkingSetManager(WorkbenchPlugin.getDefault().getBundleContext());
	}

	class ActiveWorkbenchWindowQueryContextFunction implements IContextFunction {

		private final String variable;

		public ActiveWorkbenchWindowQueryContextFunction(String variable) {
			this.variable = variable;
		}

		public Object compute(IEclipseContext context) {
			IEclipseContext child = context.getActiveChild();
			return child == null ? null : child.getLocal(variable);
		}

	}

	/**
	 * Initializes the workbench now that the display is created.
	 * 
	 * @return true if init succeeded.
	 */
	private boolean init() {
		application.getContext().set(ISources.ACTIVE_EDITOR_ID_NAME,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.ACTIVE_EDITOR_ID_NAME));
		application.getContext().set(ISources.ACTIVE_EDITOR_NAME,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.ACTIVE_EDITOR_NAME));
		application.getContext().set(ISources.ACTIVE_EDITOR_INPUT_NAME,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.ACTIVE_EDITOR_INPUT_NAME));

		application.getContext().set(ISources.SHOW_IN_INPUT,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.SHOW_IN_INPUT));
		application.getContext().set(ISources.SHOW_IN_SELECTION,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.SHOW_IN_SELECTION));

		application.getContext().set(ISources.ACTIVE_PART_NAME,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.ACTIVE_PART_NAME));
		application.getContext().set(ISources.ACTIVE_PART_ID_NAME,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.ACTIVE_PART_ID_NAME));
		application.getContext().set(ISources.ACTIVE_SITE_NAME,
				new ActiveWorkbenchWindowQueryContextFunction(ISources.ACTIVE_SITE_NAME));

		// setup debug mode if required.
		if (WorkbenchPlugin.getDefault().isDebugging()) {
			WorkbenchPlugin.DEBUG = true;
			ModalContext.setDebugMode(true);
		}

		// Set up the JFace preference store
		JFaceUtil.initializeJFacePreferences();

		// create workbench window manager
		// windowManager = new WindowManager();
		// TODO compat: I've removed the window manager, now what

		// TODO Correctly order service initialization
		// there needs to be some serious consideration given to
		// the services, and hooking them up in the correct order
		final EvaluationService evaluationService = new EvaluationService(e4Context);

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				serviceLocator.registerService(IEvaluationService.class, evaluationService);
			}
		});

		initializeLazyServices();

		// Initialize the activity support.

		activityHelper = ActivityPersistanceHelper.getInstance();
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				WorkbenchImages.getImageRegistry();
			}
		});
		initializeE4Services();
		initializeDefaultServices();
		initializeFonts();
		initializeColors();
		initializeApplicationColors();

		// now that the workbench is sufficiently initialized, let the advisor
		// have a turn.
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				advisor.internalBasicInitialize(getWorkbenchConfigurer());
			}
		});

		// configure use of color icons in toolbars
		boolean useColorIcons = PrefUtil.getInternalPreferenceStore().getBoolean(
				IPreferenceConstants.COLOR_ICONS);
		ActionContributionItem.setUseColorIconsInToolbars(useColorIcons);

		// initialize workbench single-click vs double-click behavior
		initializeSingleClickOption();

		initializeWorkbenchImages();

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				((GrabFocus) Tweaklets.get(GrabFocus.KEY)).init(getDisplay());
			}
		});

		// attempt to restore a previous workbench state
		try {
			UIStats.start(UIStats.RESTORE_WORKBENCH, "Workbench"); //$NON-NLS-1$

			final boolean bail[] = new boolean[1];
			StartupThreading.runWithoutExceptions(new StartupRunnable() {

				public void runWithException() throws Throwable {
					advisor.preStartup();
					// TODO compat: open the windows here/instantiate the model
					// TODO compat: instantiate the WW around the model
					initializationDone = true;
					// if (isClosing() || !advisor.openWindows()) {
					if (isClosing()) {
						bail[0] = true;
					}
				}
			});

			if (bail[0])
				return false;

		} finally {
			UIStats.end(UIStats.RESTORE_WORKBENCH, this, "Workbench"); //$NON-NLS-1$
		}

		// forceOpenPerspective();

		return true;
	}

	/**
	 * 
	 */
	private void initializeWorkbenchImages() {
		StartupThreading.runWithoutExceptions(new StartupRunnable() {
			public void runWithException() {
				WorkbenchImages.getDescriptors();
			}
		});
	}

	/**
	 * Establishes the relationship between JFace actions and the command
	 * manager.
	 */
	private void initializeCommandResolver() {
		ExternalActionManager.getInstance().setCallback(
				new CommandCallback(bindingManager, commandManager, new IActiveChecker() {
					public final boolean isActive(final String commandId) {
						return workbenchActivitySupport.getActivityManager().getIdentifier(
								commandId).isEnabled();
					}
				}, new IExecuteApplicable() {
					public boolean isApplicable(IAction action) {
						return !(action instanceof CommandAction);
					}
				}));
	}

	/**
	 * Initialize colors defined by the new colorDefinitions extension point.
	 * Note this will be rolled into initializeColors() at some point.
	 * 
	 * @since 3.0
	 */
	private void initializeApplicationColors() {
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				ColorDefinition[] colorDefinitions = WorkbenchPlugin.getDefault()
						.getThemeRegistry().getColors();
				ThemeElementHelper.populateRegistry(getThemeManager().getTheme(
						IThemeManager.DEFAULT_THEME), colorDefinitions, PrefUtil
						.getInternalPreferenceStore());
			}
		});
	}

	private void initializeSingleClickOption() {
		IPreferenceStore store = WorkbenchPlugin.getDefault().getPreferenceStore();
		boolean openOnSingleClick = store.getBoolean(IPreferenceConstants.OPEN_ON_SINGLE_CLICK);
		boolean selectOnHover = store.getBoolean(IPreferenceConstants.SELECT_ON_HOVER);
		boolean openAfterDelay = store.getBoolean(IPreferenceConstants.OPEN_AFTER_DELAY);
		int singleClickMethod = openOnSingleClick ? OpenStrategy.SINGLE_CLICK
				: OpenStrategy.DOUBLE_CLICK;
		if (openOnSingleClick) {
			if (selectOnHover) {
				singleClickMethod |= OpenStrategy.SELECT_ON_HOVER;
			}
			if (openAfterDelay) {
				singleClickMethod |= OpenStrategy.ARROW_KEYS_OPEN;
			}
		}
		OpenStrategy.setOpenMethod(singleClickMethod);
	}

	/*
	 * Initializes the workbench fonts with the stored values.
	 */
	private void initializeFonts() {
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				FontDefinition[] fontDefinitions = WorkbenchPlugin.getDefault().getThemeRegistry()
						.getFonts();

				ThemeElementHelper.populateRegistry(getThemeManager().getCurrentTheme(),
						fontDefinitions, PrefUtil.getInternalPreferenceStore());
			}
		});
	}

	/*
	 * Initialize the workbench images.
	 * 
	 * @param windowImages An array of the descriptors of the images to be used
	 * in the corner of each window, or <code>null</code> if none. It is
	 * expected that the array will contain the same icon, rendered at different
	 * sizes.
	 * 
	 * @since 3.0
	 */
	private static void initializeImages() {
		ImageDescriptor[] windowImages = WorkbenchPlugin.getDefault().getWindowImages();
		if (windowImages == null) {
			return;
		}

		Image[] images = new Image[windowImages.length];
		for (int i = 0; i < windowImages.length; ++i) {
			images[i] = windowImages[i].createImage();
		}
		Window.setDefaultImages(images);
	}

	/*
	 * Take the workbenches' images out of the shared registry.
	 * 
	 * @since 3.0
	 */
	private void uninitializeImages() {
		WorkbenchImages.dispose();
		Image[] images = Window.getDefaultImages();
		Window.setDefaultImage(null);
		for (int i = 0; i < images.length; i++) {
			images[i].dispose();
		}
	}

	/*
	 * Initialize the workbench colors.
	 * 
	 * @since 3.0
	 */
	private void initializeColors() {
		StartupThreading.runWithoutExceptions(new StartupRunnable() {
			public void runWithException() {
				WorkbenchColors.startup();
			}
		});
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public boolean isClosing() {
		return isClosing;
	}

	private final void initializeE4Services() {
		eventBroker.subscribe(UIEvents.buildTopic(UIEvents.ElementContainer.TOPIC,
				UIEvents.ElementContainer.CHILDREN), new EventHandler() {
			public void handleEvent(org.osgi.service.event.Event event) {
				if (application == event.getProperty(UIEvents.EventTags.ELEMENT)) {
					if (UIEvents.EventTypes.REMOVE.equals(event
							.getProperty(UIEvents.EventTags.TYPE))) {
						MWindow window = (MWindow) event.getProperty(UIEvents.EventTags.OLD_VALUE);
						IEclipseContext windowContext = window.getContext();
						if (windowContext != null) {
							IWorkbenchWindow wwindow = (IWorkbenchWindow) windowContext
									.get(IWorkbenchWindow.class.getName());
							if (wwindow != null) {
								fireWindowClosed(wwindow);
							}
						}
					}
				}
			}
		});
		eventBroker.subscribe(UIEvents.buildTopic(UIEvents.ElementContainer.TOPIC,
				UIEvents.ElementContainer.SELECTEDELEMENT), new EventHandler() {
			public void handleEvent(org.osgi.service.event.Event event) {
				if (application == event.getProperty(UIEvents.EventTags.ELEMENT)) {
					if (UIEvents.EventTypes.SET.equals(event
							.getProperty(UIEvents.EventTags.TYPE))) {
						MWindow window = (MWindow) event.getProperty(UIEvents.EventTags.NEW_VALUE);
						if (window != null) {
							IWorkbenchWindow wwindow = (IWorkbenchWindow) window.getContext().get(
									IWorkbenchWindow.class.getName());
							if (wwindow != null) {
								e4Context.set(ISources.ACTIVE_WORKBENCH_WINDOW_NAME, wwindow);
								e4Context.set(ISources.ACTIVE_WORKBENCH_WINDOW_SHELL_NAME,
										wwindow.getShell());
							}
						}
					}
				}
			}
		});

		// watch for parts' "toBeRendered" attribute being flipped to true, if
		// they need to be rendered, then they need a corresponding 3.x
		// reference
		eventBroker.subscribe(UIEvents.buildTopic(UIEvents.UIElement.TOPIC,
				UIEvents.UIElement.TOBERENDERED), new EventHandler() {
			public void handleEvent(org.osgi.service.event.Event event) {
				if (Boolean.TRUE.equals(event.getProperty(UIEvents.EventTags.NEW_VALUE))) {
					Object element = event.getProperty(UIEvents.EventTags.ELEMENT);
					if (element instanceof MPart) {
						MPart part = (MPart) element;
						createReference(part);
					}
				}
			}
		});

		// watch for parts' contexts being set, once they've been set, we need
		// to inject the ViewReference/EditorReference into the context
		eventBroker.subscribe(
				UIEvents.buildTopic(UIEvents.Context.TOPIC, UIEvents.Context.CONTEXT),
				new EventHandler() {
					public void handleEvent(org.osgi.service.event.Event event) {
						Object element = event.getProperty(UIEvents.EventTags.ELEMENT);
						if (element instanceof MPart) {
							MPart part = (MPart) element;
							IEclipseContext context = part.getContext();
							if (context != null) {
								setReference(part, context);
							}
						}
					}
				});
		
		MPartDescriptor descriptor = org.eclipse.e4.ui.model.application.descriptor.basic.impl.BasicFactoryImpl.eINSTANCE
				.createPartDescriptor();
		descriptor.getTags().add("Editor"); //$NON-NLS-1$
		descriptor.setCloseable(true);
		descriptor.setAllowMultiple(true);
		descriptor.setElementId(CompatibilityEditor.MODEL_ELEMENT_ID);
		descriptor.setContributionURI(CompatibilityPart.COMPATIBILITY_EDITOR_URI);
		descriptor.setCategory("org.eclipse.e4.primaryDataStack"); //$NON-NLS-1$
		application.getDescriptors().add(descriptor);

		WorkbenchPlugin.getDefault().getViewRegistry();
	}

	/**
	 * Returns a workbench page that will contain the specified part. If no page
	 * can be located, one will be instantiated.
	 * 
	 * @param part
	 *            the model part to query a parent workbench page for
	 * @return the workbench page that contains the specified part
	 */
	private WorkbenchPage getWorkbenchPage(MPart part) {
		IEclipseContext context = getWindowContext(part);
		WorkbenchPage page = (WorkbenchPage) context.get(IWorkbenchPage.class.getName());
		if (page == null) {
			MWindow window = (MWindow) context.get(MWindow.class.getName());
			Workbench workbench = (Workbench) PlatformUI.getWorkbench();
			workbench.openWorkbenchWindow(getDefaultPageInput(), getPerspectiveRegistry()
					.findPerspectiveWithId(getDefaultPerspectiveId()),
					window, false);
			page = (WorkbenchPage) context.get(IWorkbenchPage.class.getName());
		}
		return page;
	}

	/**
	 * Sets the 3.x reference of the specified part into its context.
	 * 
	 * @param part
	 *            the model part that requires a 3.x part reference
	 * @param context
	 *            the part's context
	 */
	private void setReference(MPart part, IEclipseContext context) {
		String uri = part.getContributionURI();
		if (CompatibilityPart.COMPATIBILITY_VIEW_URI.equals(uri)) {
			WorkbenchPage page = getWorkbenchPage(part);
			ViewReference ref = page.getViewReference(part);
			if (ref == null) {
				ref = createViewReference(part, page);
			}
			context.set(ViewReference.class.getName(), ref);
		} else if (CompatibilityPart.COMPATIBILITY_EDITOR_URI.equals(uri)) {
			WorkbenchPage page = getWorkbenchPage(part);
			EditorReference ref = page.getEditorReference(part);
			if (ref == null) {
				ref = createEditorReference(part, page);
			}
			context.set(EditorReference.class.getName(), ref);
		}
	}

	private ViewReference createViewReference(MPart part, WorkbenchPage page) {
		WorkbenchWindow window = (WorkbenchWindow) page.getWorkbenchWindow();
		IViewDescriptor desc = window.getWorkbench().getViewRegistry().find(part.getElementId());
		ViewReference ref = new ViewReference(window.getModel().getContext(), page, part,
				(ViewDescriptor) desc);
		page.addViewReference(ref);
		return ref;
	}

	private EditorReference createEditorReference(MPart part, WorkbenchPage page) {
		WorkbenchWindow window = (WorkbenchWindow) page.getWorkbenchWindow();
		EditorReference ref = new EditorReference(window.getModel().getContext(), page, part, null,
				null, null);
		page.addEditorReference(ref);
		return ref;
	}

	/**
	 * Creates a workbench part reference for the specified part if one does not
	 * already exist.
	 * 
	 * @param part
	 *            the model part to create a 3.x part reference for
	 */
	private void createReference(MPart part) {
		String uri = part.getContributionURI();
		if (CompatibilityPart.COMPATIBILITY_VIEW_URI.equals(uri)) {
			WorkbenchPage page = getWorkbenchPage(part);
			ViewReference ref = page.getViewReference(part);
			if (ref == null) {
				createViewReference(part, page);
			}
		} else if (CompatibilityPart.COMPATIBILITY_EDITOR_URI.equals(uri)) {
			WorkbenchPage page = getWorkbenchPage(part);
			EditorReference ref = page.getEditorReference(part);
			if (ref == null) {
				createEditorReference(part, page);
			}
		}
	}

	private IEclipseContext getWindowContext(MPart part) {
		MElementContainer<?> parent = (MElementContainer<?>) ((EObject) part).eContainer();
		while (!(parent instanceof MWindow)) {
			parent = (MElementContainer<?>) ((EObject) parent).eContainer(); // parent.getParent();
		}

		return ((MWindow) parent).getContext();
	}

	private final void initializeLazyServices() {
		e4Context.set(IExtensionTracker.class.getName(), new ContextFunction() {

			public Object compute(IEclipseContext context) {
				if (tracker == null) {
					tracker = new UIExtensionTracker(getDisplay());
				}
				return tracker;
			}
		});
		e4Context.set(IWorkbenchActivitySupport.class.getName(), new ContextFunction() {

			public Object compute(IEclipseContext context) {
				if (workbenchActivitySupport == null) {
					workbenchActivitySupport = new WorkbenchActivitySupport();
				}
				return workbenchActivitySupport;
			}
		});
		e4Context.set(IProgressService.class.getName(), new ContextFunction() {
			@Override
			public Object compute(IEclipseContext context) {
				return ProgressManager.getInstance();
			}
		});
		WorkbenchPlugin.getDefault().initializeContext(e4Context);
	}

	static class MakeHandlersGo extends AbstractHandler {

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands
		 * .ExecutionEvent)
		 */
		public Object execute(ExecutionEvent event) throws ExecutionException {
			org.eclipse.e4.ui.internal.workbench.Activator.trace(
					org.eclipse.e4.ui.internal.workbench.Policy.DEBUG_CMDS,
					"AllHandlerGo: not for executing", null); //$NON-NLS-1$
			return null;
		}

	}

	private ArrayList<MCommand> commandsToRemove = new ArrayList<MCommand>();
	private ArrayList<MCategory> categoriesToRemove = new ArrayList<MCategory>();

	private CommandService initializeCommandService(IEclipseContext appContext) {
		CommandService service = new CommandService(commandManager);
		appContext.set(ICommandService.class.getName(), service);
		service.readRegistry();

		MakeHandlersGo allHandlers = new MakeHandlersGo();

		Command[] cmds = commandManager.getAllCommands();
		for (int i = 0; i < cmds.length; i++) {
			Command cmd = cmds[i];
			cmd.setHandler(allHandlers);
		}

		return service;
	}

	private void defineBindingTable(String id) {
		List<MBindingTable> bindingTables = application.getBindingTables();
		if (contains(bindingTables, id)) {
			return;
		}
		WorkbenchPlugin.log("Defining a binding table: " + id); //$NON-NLS-1$
		MBindingTable bt = CommandsFactoryImpl.eINSTANCE.createBindingTable();
		bt.setBindingContextId(id);
		bindingTables.add(bt);
	}

	/**
	 * @param bindingTables
	 * @param id
	 * @return true if this BT already exists
	 */
	private boolean contains(List<MBindingTable> bindingTables, String id) {
		for (MBindingTable bt : bindingTables) {
			if (id.equals(bt.getBindingContextId())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Initializes all of the default services for the workbench. For
	 * initializing the command-based services, this also parses the registry
	 * and hooks up all the required listeners.
	 */
	private final void initializeDefaultServices() {

		final IContributionService contributionService = new ContributionService(getAdvisor());
		serviceLocator.registerService(IContributionService.class, contributionService);

		// TODO Correctly order service initialization
		// there needs to be some serious consideration given to
		// the services, and hooking them up in the correct order
		final IEvaluationService evaluationService = (IEvaluationService) serviceLocator
				.getService(IEvaluationService.class);

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				serviceLocator.registerService(ISaveablesLifecycleListener.class,
						new SaveablesList());
			}
		});

		/*
		 * Phase 1 of the initialization of commands. When this phase completes,
		 * all the services and managers will exist, and be accessible via the
		 * getService(Object) method.
		 */
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				Command.DEBUG_COMMAND_EXECUTION = Policy.DEBUG_COMMANDS;
				commandManager = e4Context.get(CommandManager.class);
			}
		});

		final CommandService[] commandService = new CommandService[1];
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				commandService[0] = initializeCommandService(e4Context);

			}
		});

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				ContextManager.DEBUG = Policy.DEBUG_CONTEXTS;
				contextManager = e4Context.get(ContextManager.class);
			}
		});

		IContextService cxs = (IContextService) ContextInjectionFactory.make(ContextService.class,
				e4Context);

		final IContextService contextService = cxs;

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				contextManager.addContextManagerListener(new IContextManagerListener() {
					public void contextManagerChanged(ContextManagerEvent contextManagerEvent) {
						if (contextManagerEvent.isContextChanged()) {
							String id = contextManagerEvent.getContextId();
							if (id != null) {
								defineBindingTable(id);
							}
						}
					}
				});
				EContextService ecs = e4Context.get(EContextService.class);
				ecs.activateContext(IContextService.CONTEXT_ID_DIALOG_AND_WINDOW);
			}
		});

		serviceLocator.registerService(IContextService.class, contextService);

		final IBindingService[] bindingService = new BindingService[1];

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				BindingManager.DEBUG = Policy.DEBUG_KEY_BINDINGS;
				bindingManager = new BindingManager(contextManager, commandManager);
				serviceLocator.registerService(BindingManager.class, bindingManager);
				bindingService[0] = ContextInjectionFactory.make(
						BindingService.class, e4Context);
			}
		});

		bindingService[0].readRegistryAndPreferences(commandService[0]);
		serviceLocator.registerService(IBindingService.class, bindingService[0]);

		final CommandImageManager commandImageManager = new CommandImageManager();
		final CommandImageService commandImageService = new CommandImageService(
				commandImageManager, commandService[0]);
		commandImageService.readRegistry();
		serviceLocator.registerService(ICommandImageService.class, commandImageService);

		final WorkbenchMenuService menuService = new WorkbenchMenuService(serviceLocator, e4Context);

		serviceLocator.registerService(IMenuService.class, menuService);
		// the service must be registered before it is initialized - its
		// initialization uses the service locator to address a dependency on
		// the menu service
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				menuService.readRegistry();
			}
		});

		/*
		 * Phase 2 of the initialization of commands. The source providers that
		 * the workbench provides are creating and registered with the above
		 * services. These source providers notify the services when particular
		 * pieces of workbench state change.
		 */
		final SourceProviderService sourceProviderService = new SourceProviderService(
				serviceLocator);
		serviceLocator.registerService(ISourceProviderService.class, sourceProviderService);
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				// this currently instantiates all players ... sigh
				sourceProviderService.readRegistry();
				ISourceProvider[] sp = sourceProviderService.getSourceProviders();
				for (int i = 0; i < sp.length; i++) {
					evaluationService.addSourceProvider(sp[i]);
					if (!(sp[i] instanceof ActiveContextSourceProvider)) {
						contextService.addSourceProvider(sp[i]);
					}
				}
			}
		});

		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				// these guys are need to provide the variables they say
				// they source

				FocusControlSourceProvider focusControl = (FocusControlSourceProvider) sourceProviderService
						.getSourceProvider(ISources.ACTIVE_FOCUS_CONTROL_ID_NAME);
				serviceLocator.registerService(IFocusService.class, focusControl);

				menuSourceProvider = (MenuSourceProvider) sourceProviderService
						.getSourceProvider(ISources.ACTIVE_MENU_NAME);
			}
		});

		/*
		 * Phase 3 of the initialization of commands. This handles the creation
		 * of wrappers for legacy APIs. By the time this phase completes, any
		 * code trying to access commands through legacy APIs should work.
		 */
		final IHandlerService[] handlerService = new IHandlerService[1];
		StartupThreading.runWithoutExceptions(new StartupRunnable() {

			public void runWithException() {
				handlerService[0] = new LegacyHandlerService(e4Context);
				e4Context.set(IHandlerService.class.getName(), handlerService[0]);
				handlerService[0].readRegistry();
			}
		});
		workbenchContextSupport = new WorkbenchContextSupport(this, contextManager);
		workbenchCommandSupport = new WorkbenchCommandSupport(bindingManager, commandManager,
				contextManager, handlerService[0]);
		initializeCommandResolver();

		// addWindowListener(windowListener);
		// bindingManager.addBindingManagerListener(bindingManagerListener);

		serviceLocator.registerService(ISelectionConversionService.class,
				new SelectionConversionService());
	}

	/**
	 * Returns true if the Workbench is in the process of starting.
	 * 
	 * @return <code>true</code> if the Workbench is starting, but not yet
	 *         running the event loop.
	 */
	public boolean isStarting() {
		return isStarting && isRunning();
	}

	/*
	 * If a perspective was specified on the command line (-perspective) then
	 * force that perspective to open in the active window.
	 */
	void forceOpenPerspective() {
		if (getWorkbenchWindowCount() == 0) {
			// there should be an open window by now, bail out.
			return;
		}

		String perspId = null;
		String[] commandLineArgs = Platform.getCommandLineArgs();
		for (int i = 0; i < commandLineArgs.length - 1; i++) {
			if (commandLineArgs[i].equalsIgnoreCase("-perspective")) { //$NON-NLS-1$
				perspId = commandLineArgs[i + 1];
				break;
			}
		}
		if (perspId == null) {
			return;
		}
		IPerspectiveDescriptor desc = getPerspectiveRegistry().findPerspectiveWithId(perspId);
		if (desc == null) {
			return;
		}

		IWorkbenchWindow win = getActiveWorkbenchWindow();
		if (win == null) {
			win = getWorkbenchWindows()[0];
		}

		final String threadPerspId = perspId;
		final IWorkbenchWindow threadWin = win;
		StartupThreading.runWithoutExceptions(new StartupRunnable() {
			public void runWithException() throws Throwable {
				try {
					showPerspective(threadPerspId, threadWin);
				} catch (WorkbenchException e) {
					String msg = "Workbench exception showing specified command line perspective on startup."; //$NON-NLS-1$
					WorkbenchPlugin.log(msg, new Status(IStatus.ERROR, PlatformUI.PLUGIN_ID, 0,
							msg, e));
				}
			}
		});
	}

	/**
	 * Opens the initial workbench window.
	 */
	/* package */void openFirstTimeWindow() {
		final boolean showProgress = PrefUtil.getAPIPreferenceStore().getBoolean(
				IWorkbenchPreferenceConstants.SHOW_PROGRESS_ON_STARTUP);

		if (!showProgress) {
			doOpenFirstTimeWindow();
		} else {
			// We don't know how many plug-ins will be loaded,
			// assume we are loading a tenth of the installed plug-ins.
			// (The Eclipse SDK loads 7 of 86 plug-ins at startup as of
			// 2005-5-20)
			final int expectedProgressCount = Math.max(1, WorkbenchPlugin.getDefault()
					.getBundleCount() / 10);

			runStartupWithProgress(expectedProgressCount, new Runnable() {
				public void run() {
					doOpenFirstTimeWindow();
				}
			});
		}
	}

	private void doOpenFirstTimeWindow() {
		try {
			final IAdaptable input[] = new IAdaptable[1];
			StartupThreading.runWithoutExceptions(new StartupRunnable() {

				public void runWithException() throws Throwable {
					input[0] = getDefaultPageInput();
				}
			});

			openWorkbenchWindow(getDefaultPerspectiveId(), input[0]);
		} catch (final WorkbenchException e) {
			// Don't use the window's shell as the dialog parent,
			// as the window is not open yet (bug 76724).
			StartupThreading.runWithoutExceptions(new StartupRunnable() {

				public void runWithException() throws Throwable {
					ErrorDialog.openError(null, WorkbenchMessages.Problems_Opening_Page, e
							.getMessage(), e.getStatus());
				}
			});
		}
	}

	private void runStartupWithProgress(final int expectedProgressCount, final Runnable runnable) {
		progressCount = 0;
		final double cutoff = 0.95;

		AbstractSplashHandler handler = getSplash();
		IProgressMonitor progressMonitor = null;
		if (handler != null)
			progressMonitor = handler.getBundleProgressMonitor();

		if (progressMonitor == null) {
			// cannot report progress (e.g. if the splash screen is not showing)
			// fall back to starting without showing progress.
			runnable.run();
		} else {
			progressMonitor.beginTask("", expectedProgressCount); //$NON-NLS-1$
			SynchronousBundleListener bundleListener = new StartupProgressBundleListener(
					progressMonitor, (int) (expectedProgressCount * cutoff));
			WorkbenchPlugin.getDefault().addBundleListener(bundleListener);
			try {
				runnable.run();
				progressMonitor.subTask(WorkbenchMessages.Startup_Done);
				int remainingWork = expectedProgressCount
						- Math.min(progressCount, (int) (expectedProgressCount * cutoff));
				progressMonitor.worked(remainingWork);
				progressMonitor.done();
			} finally {
				WorkbenchPlugin.getDefault().removeBundleListener(bundleListener);
			}
		}
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow openWorkbenchWindow(IAdaptable input) throws WorkbenchException {
		return openWorkbenchWindow(getDefaultPerspectiveId(), input);
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchWindow openWorkbenchWindow(String perspectiveId, IAdaptable input)
			throws WorkbenchException {
		IPerspectiveDescriptor descriptor = getPerspectiveRegistry().findPerspectiveWithId(
				perspectiveId);
		if (descriptor == null) {
			throw new WorkbenchException(NLS.bind(
					WorkbenchMessages.WorkbenchPage_ErrorCreatingPerspective, perspectiveId));
		}

		try {
			MWindow window = BasicFactoryImpl.eINSTANCE.createTrimmedWindow();
			return openWorkbenchWindow(input, descriptor, window, true);
		} catch (InjectionException e) {
			throw new WorkbenchException(e.getMessage(), e);
		}
	}

	public WorkbenchWindow openWorkbenchWindow(IAdaptable input, IPerspectiveDescriptor descriptor,
			MWindow window, boolean newWindow) {
		WorkbenchWindow result = (WorkbenchWindow) createWorkbenchWindow(input, descriptor, window,
				newWindow);
		fireWindowOpened(result);
		return result;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public boolean restart() {
		try {
			for (IWorkbenchWindow window : getWorkbenchWindows()) {
				((WorkbenchWindow) window).setRestarting(true);
			}
			IPresentationEngine engine = (IPresentationEngine) application.getContext().get(
					IPresentationEngine.SERVICE_NAME);
			engine.stop();
			returnCode = PlatformUI.RETURN_RESTART;
		} finally {
			for (IWorkbenchWindow window : getWorkbenchWindows()) {
				((WorkbenchWindow) window).setRestarting(false);
			}
		}
		return true;
	}



	/**
	 * Returns the ids of all plug-ins that extend the
	 * <code>org.eclipse.ui.startup</code> extension point.
	 * 
	 * @return the ids of all plug-ins containing 1 or more startup extensions
	 */
	public String[] getEarlyActivatedPlugins() {
		IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(
				PlatformUI.PLUGIN_ID, IWorkbenchRegistryConstants.PL_STARTUP);
		IExtension[] extensions = point.getExtensions();
		ArrayList pluginIds = new ArrayList(extensions.length);
		for (int i = 0; i < extensions.length; i++) {
			String id = extensions[i].getNamespace();
			if (!pluginIds.contains(id)) {
				pluginIds.add(id);
			}
		}
		return (String[]) pluginIds.toArray(new String[pluginIds.size()]);
	}

	/**
	 * Returns the ids of the early activated plug-ins that have been disabled
	 * by the user.
	 * 
	 * @return the ids of the early activated plug-ins that have been disabled
	 *         by the user
	 */
	public String[] getDisabledEarlyActivatedPlugins() {
		String pref = PrefUtil.getInternalPreferenceStore().getString(
				IPreferenceConstants.PLUGINS_NOT_ACTIVATED_ON_STARTUP);
		return Util.getArrayFromList(pref, ";"); //$NON-NLS-1$
	}

	/*
	 * Starts all plugins that extend the <code> org.eclipse.ui.startup </code>
	 * extension point, and that the user has not disabled via the preference
	 * page.
	 */
	private void startPlugins() {
		IExtensionRegistry registry = Platform.getExtensionRegistry();

		// bug 55901: don't use getConfigElements directly, for pre-3.0
		// compat, make sure to allow both missing class
		// attribute and a missing startup element
		IExtensionPoint point = registry.getExtensionPoint(PlatformUI.PLUGIN_ID,
				IWorkbenchRegistryConstants.PL_STARTUP);

		final IExtension[] extensions = point.getExtensions();
		if (extensions.length == 0) {
			return;
		}
		Job job = new Job("Workbench early startup") { //$NON-NLS-1$
			protected IStatus run(IProgressMonitor monitor) {
				HashSet disabledPlugins = new HashSet(Arrays
						.asList(getDisabledEarlyActivatedPlugins()));
				monitor.beginTask(WorkbenchMessages.Workbench_startingPlugins, extensions.length);
				for (int i = 0; i < extensions.length; ++i) {
					if (monitor.isCanceled() || !isRunning()) {
						return Status.CANCEL_STATUS;
					}
					IExtension extension = extensions[i];

					// if the plugin is not in the set of disabled plugins, then
					// execute the code to start it
					if (!disabledPlugins.contains(extension.getNamespace())) {
						monitor.subTask(extension.getNamespace());
						SafeRunner.run(new EarlyStartupRunnable(extension));
					}
					monitor.worked(1);
				}
				monitor.done();
				return Status.OK_STATUS;
			}

			public boolean belongsTo(Object family) {
				return EARLY_STARTUP_FAMILY.equals(family);
			}
		};
		job.setSystem(true);
		job.schedule();
	}

	/**
	 * Internal method for running the workbench UI. This entails processing and
	 * dispatching events until the workbench is closed or restarted.
	 * 
	 * @return return code {@link PlatformUI#RETURN_OK RETURN_OK}for normal
	 *         exit; {@link PlatformUI#RETURN_RESTART RETURN_RESTART}if the
	 *         workbench was terminated with a call to
	 *         {@link IWorkbench#restart IWorkbench.restart};
	 *         {@link PlatformUI#RETURN_UNSTARTABLE RETURN_UNSTARTABLE}if the
	 *         workbench could not be started; other values reserved for future
	 *         use
	 * @since 3.0
	 */
	private int runUI() {
		UIStats.start(UIStats.START_WORKBENCH, "Workbench"); //$NON-NLS-1$

		// deadlock code
		boolean avoidDeadlock = true;

		String[] commandLineArgs = Platform.getCommandLineArgs();
		for (int i = 0; i < commandLineArgs.length; i++) {
			if (commandLineArgs[i].equalsIgnoreCase("-allowDeadlock")) { //$NON-NLS-1$
				avoidDeadlock = false;
			}
		}

		final UISynchronizer synchronizer;

		if (avoidDeadlock) {
			UILockListener uiLockListener = new UILockListener(display);
			Job.getJobManager().setLockListener(uiLockListener);
			synchronizer = new UISynchronizer(display, uiLockListener);
			display.setSynchronizer(synchronizer);
			// declare the main thread to be a startup thread.
			UISynchronizer.startupThread.set(Boolean.TRUE);
		} else
			synchronizer = null;

		// prime the splash nice and early
		if (createSplash)
			createSplashWrapper();

		// ModalContext should not spin the event loop (there is no UI yet to
		// block)
		ModalContext.setAllowReadAndDispatch(false);

		// if the -debug command line argument is used and the event loop is
		// being
		// run while starting the Workbench, log a warning.
		if (WorkbenchPlugin.getDefault().isDebugging()) {
			display.asyncExec(new Runnable() {
				public void run() {
					if (isStarting()) {
						WorkbenchPlugin.log(StatusUtil.newStatus(IStatus.WARNING,
								"Event loop should not be run while the Workbench is starting.", //$NON-NLS-1$
								new RuntimeException()));
					}
				}
			});
		}

		Listener closeListener = new Listener() {
			public void handleEvent(Event event) {
				event.doit = close();
			}
		};

		// Initialize an exception handler.
		Window.IExceptionHandler handler = ExceptionHandler.getInstance();

		try {
			// react to display close event by closing workbench nicely
			display.addListener(SWT.Close, closeListener);

			// install backstop to catch exceptions thrown out of event loop
			Window.setExceptionHandler(handler);

			final boolean[] initOK = new boolean[1];

			if (getSplash() != null) {

				final boolean[] initDone = new boolean[] { false };
				final Throwable[] error = new Throwable[1];
				Thread initThread = new Thread() {
					/*
					 * (non-Javadoc)
					 * 
					 * @see java.lang.Thread#run()
					 */
					public void run() {
						try {
							// declare us to be a startup thread so that our
							// syncs will be executed
							UISynchronizer.startupThread.set(Boolean.TRUE);
							initOK[0] = Workbench.this.init();
						} catch (Throwable e) {
							error[0] = e;
						} finally {
							initDone[0] = true;
							display.wake();
						}
					}
				};
				initThread.start();
				while (true) {
					if (!display.readAndDispatch()) {
						if (initDone[0])
							break;
						display.sleep();
					}
				}
				Throwable throwable = error[0];
				if (throwable != null) {
					if (throwable instanceof Error)
						throw (Error) throwable;
					if (throwable instanceof Exception)
						throw (Exception) throwable;

					// how very exotic - something that isn't playing by the
					// rules. Wrap it in an error and bail
					throw new Error(throwable);
				}
			} else {
				// initialize workbench and restore or open one window
				initOK[0] = init();

			}
			// drop the splash screen now that a workbench window is up
			Platform.endSplash();

			// let the advisor run its start up code
			if (initOK[0]) {
				advisor.postStartup(); // may trigger a close/restart
			}

			if (initOK[0] && runEventLoop) {
				workbenchService = WorkbenchPlugin.getDefault().getBundleContext().registerService(
						IWorkbench.class.getName(), this, null);
				// start eager plug-ins
				startPlugins();
				addStartupRegistryListener();

				// WWinPluginAction.refreshActionList();

				display.asyncExec(new Runnable() {
					public void run() {
						UIStats.end(UIStats.START_WORKBENCH, this, "Workbench"); //$NON-NLS-1$
						UIStats.startupComplete();
					}
				});

				getWorkbenchTestable().init(display, this);

				// allow ModalContext to spin the event loop
				ModalContext.setAllowReadAndDispatch(true);
				isStarting = false;

				if (synchronizer != null)
					synchronizer.started();
				// the event loop
				// runEventLoop(handler, display);
			}
			returnCode = PlatformUI.RETURN_OK;
		} catch (final Exception e) {
			if (!display.isDisposed()) {
				handler.handleException(e);
			} else {
				String msg = "Exception in Workbench.runUI after display was disposed"; //$NON-NLS-1$
				WorkbenchPlugin.log(msg, new Status(IStatus.ERROR, WorkbenchPlugin.PI_WORKBENCH, 1,
						msg, e));
			}
		}

		// restart or exit based on returnCode
		return returnCode;
	}



	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchPage showPerspective(String perspectiveId, IWorkbenchWindow window)
			throws WorkbenchException {
		return showPerspective(perspectiveId, window, null);
	}

	private boolean activate(String perspectiveId, IWorkbenchPage page, IAdaptable input,
			boolean checkPerspective) {
		if (page != null) {
			for (IPerspectiveDescriptor openedPerspective : page.getOpenPerspectives()) {
				if (!checkPerspective || openedPerspective.getId().equals(perspectiveId)) {
					if (page.getInput() == input) {
						WorkbenchWindow wwindow = (WorkbenchWindow) page.getWorkbenchWindow();
						MWindow model = wwindow.getModel();
						application.setSelectedElement(model);
						return true;
					}
				}
			}
		}
		return false;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IWorkbenchPage showPerspective(String perspectiveId, IWorkbenchWindow targetWindow,
			IAdaptable input) throws WorkbenchException {
		Assert.isNotNull(perspectiveId);
		IPerspectiveDescriptor targetPerspective = getPerspectiveRegistry().findPerspectiveWithId(
				perspectiveId);
		if (targetPerspective == null) {
			throw new WorkbenchException(NLS.bind(
					WorkbenchMessages.WorkbenchPage_ErrorCreatingPerspective, perspectiveId));
		}

		if (targetWindow != null) {
			IWorkbenchPage page = targetWindow.getActivePage();
			if (activate(perspectiveId, page, input, true)) {
				return page;
			}
		}

		for (IWorkbenchWindow window : getWorkbenchWindows()) {
			IWorkbenchPage page = window.getActivePage();
			if (activate(perspectiveId, page, input, true)) {
				return page;
			}
		}

		if (targetWindow != null) {
			IWorkbenchPage page = targetWindow.getActivePage();
			if (activate(perspectiveId, page, input, false)) {
				return page;
			}
			IPreferenceStore store = WorkbenchPlugin.getDefault().getPreferenceStore();
			int mode = store.getInt(IPreferenceConstants.OPEN_PERSP_MODE);

			if (IPreferenceConstants.OPM_NEW_WINDOW != mode) {
				targetWindow.getShell().open();
				if (page == null) {
					page = targetWindow.openPage(perspectiveId, input);
				} else {
					page.setPerspective(targetPerspective);
				}
				return page;
			}
		}

		return openWorkbenchWindow(perspectiveId, input).getActivePage();
	}

	/*
	 * Shuts down the application.
	 */
	private void shutdown() {
		// shutdown application-specific portions first
		try {
			advisor.postShutdown();
		} catch (Exception ex) {
			StatusManager.getManager().handle(
					StatusUtil.newStatus(WorkbenchPlugin.PI_WORKBENCH,
							"Exceptions during shutdown", ex)); //$NON-NLS-1$
		}

		// notify regular workbench clients of shutdown, and clear the list when
		// done
		firePostShutdown();
		workbenchListeners.clear();

		cancelEarlyStartup();
		if (workbenchService != null)
			workbenchService.unregister();

		// for dynamic UI
		Platform.getExtensionRegistry().removeRegistryChangeListener(extensionEventHandler);
		Platform.getExtensionRegistry().removeRegistryChangeListener(startupRegistryListener);

		((GrabFocus) Tweaklets.get(GrabFocus.KEY)).dispose();

		// Bring down all of the services.
		serviceLocator.dispose();
		application.getCommands().removeAll(commandsToRemove);
		application.getCategories().removeAll(categoriesToRemove);

		workbenchActivitySupport.dispose();
		WorkbenchHelpSystem.disposeIfNecessary();

		// shutdown the rest of the workbench
		WorkbenchColors.shutdown();
		activityHelper.shutdown();
		uninitializeImages();
		if (WorkbenchPlugin.getDefault() != null) {
			WorkbenchPlugin.getDefault().reset();
		}
		WorkbenchThemeManager.getInstance().dispose();
		PropertyPageContributorManager.getManager().dispose();
		ObjectActionContributorManager.getManager().dispose();
		if (tracker != null) {
			tracker.close();
		}
	}

	/**
	 * Cancels the early startup job, if it's still running.
	 */
	private void cancelEarlyStartup() {
		Job.getJobManager().cancel(EARLY_STARTUP_FAMILY);
		// We do not currently wait for any plug-in currently being started to
		// complete
		// (e.g. by doing a join on EARLY_STARTUP_FAMILY), since they may do a
		// syncExec,
		// which would hang. See bug 94537 for rationale.
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public IDecoratorManager getDecoratorManager() {
		return WorkbenchPlugin.getDefault().getDecoratorManager();
	}


	/*
	 * Sets the workbench window which was last known being the active one, or
	 * <code> null </code> .
	 */
	/* package */
	void setActivatedWindow(WorkbenchWindow window) {
		activatedWindow = window;
	}

	/**
	 * Returns the unique object that applications use to configure the
	 * workbench.
	 * <p>
	 * IMPORTANT This method is declared package-private to prevent regular
	 * plug-ins from downcasting IWorkbench to Workbench and getting hold of the
	 * workbench configurer that would allow them to tamper with the workbench.
	 * The workbench configurer is available only to the application.
	 * </p>
	 */
	/* package */
	WorkbenchConfigurer getWorkbenchConfigurer() {
		if (workbenchConfigurer == null) {
			workbenchConfigurer = new WorkbenchConfigurer();
		}
		return workbenchConfigurer;
	}

	/**
	 * Returns the workbench advisor that created this workbench.
	 * <p>
	 * IMPORTANT This method is declared package-private to prevent regular
	 * plug-ins from downcasting IWorkbench to Workbench and getting hold of the
	 * workbench advisor that would allow them to tamper with the workbench. The
	 * workbench advisor is internal to the application.
	 * </p>
	 */
	/* package */
	WorkbenchAdvisor getAdvisor() {
		return advisor;
	}

	/*
	 * (non-Javadoc) Method declared on IWorkbench.
	 */
	public Display getDisplay() {
		return display;
	}

	/**
	 * Returns the default perspective id, which may be <code>null</code>.
	 * 
	 * @return the default perspective id, or <code>null</code>
	 */
	public String getDefaultPerspectiveId() {
		String defaultId = getPerspectiveRegistry().getDefaultPerspective();
		return defaultId == null ? getAdvisor().getInitialWindowPerspectiveId() : defaultId;
	}

	/**
	 * Returns the default workbench window page input.
	 * 
	 * @return the default window page input or <code>null</code> if none
	 */
	public IAdaptable getDefaultPageInput() {
		return getAdvisor().getDefaultPageInput();
	}

	/**
	 * Returns the id of the preference page that should be presented most
	 * prominently.
	 * 
	 * @return the id of the preference page, or <code>null</code> if none
	 */
	public String getMainPreferencePageId() {
		String id = getAdvisor().getMainPreferencePageId();
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench
	 * 
	 * @since 3.0
	 */
	public IElementFactory getElementFactory(String factoryId) {
		Assert.isNotNull(factoryId);
		return WorkbenchPlugin.getDefault().getElementFactory(factoryId);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getProgressService()
	 */
	public IProgressService getProgressService() {
		return (IProgressService) e4Context.get(IProgressService.class.getName());
	}

	private WorkbenchActivitySupport workbenchActivitySupport;

	private WorkbenchCommandSupport workbenchCommandSupport;

	private WorkbenchContextSupport workbenchContextSupport;

	/**
	 * The single instance of the binding manager used by the workbench. This is
	 * initialized in <code>Workbench.init(Display)</code> and then never
	 * changed. This value will only be <code>null</code> if the initialization
	 * call has not yet completed.
	 * 
	 * @since 3.1
	 */
	private BindingManager bindingManager;

	/**
	 * The single instance of the command manager used by the workbench. This is
	 * initialized in <code>Workbench.init(Display)</code> and then never
	 * changed. This value will only be <code>null</code> if the initialization
	 * call has not yet completed.
	 * 
	 * @since 3.1
	 */
	private CommandManager commandManager;

	/**
	 * The single instance of the context manager used by the workbench. This is
	 * initialized in <code>Workbench.init(Display)</code> and then never
	 * changed. This value will only be <code>null</code> if the initialization
	 * call has not yet completed.
	 * 
	 * @since 3.1
	 */
	private ContextManager contextManager;

	public IWorkbenchActivitySupport getActivitySupport() {
		return (IWorkbenchActivitySupport) e4Context.get(IWorkbenchActivitySupport.class.getName());
	}

	public IWorkbenchCommandSupport getCommandSupport() {
		return workbenchCommandSupport;
	}

	public IWorkbenchContextSupport getContextSupport() {
		return workbenchContextSupport;
	}

	/**
	 * This method should not be called outside the framework.
	 * 
	 * @return The context manager.
	 */
	public ContextManager getContextManager() {
		return contextManager;
	}



	private ActivityPersistanceHelper activityHelper;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getIntroManager()
	 */
	public IIntroManager getIntroManager() {
		return getWorkbenchIntroManager();
	}

	/**
	 * @return the workbench intro manager
	 * @since 3.0
	 */
	/* package */WorkbenchIntroManager getWorkbenchIntroManager() {
		if (introManager == null) {
			introManager = new WorkbenchIntroManager(this);
		}
		return introManager;
	}

	private WorkbenchIntroManager introManager;

	/**
	 * @return the intro extension for this workbench.
	 * 
	 * @since 3.0
	 */
	public IntroDescriptor getIntroDescriptor() {
		return introDescriptor;
	}

	/**
	 * This method exists as a test hook. This method should
	 * <strong>NEVER</strong> be called by clients.
	 * 
	 * @param descriptor
	 *            The intro descriptor to use.
	 * @since 3.0
	 */
	public void setIntroDescriptor(IntroDescriptor descriptor) {
		if (getIntroManager().getIntro() != null) {
			getIntroManager().closeIntro(getIntroManager().getIntro());
		}
		introDescriptor = descriptor;
	}

	/**
	 * The descriptor for the intro extension that is valid for this workspace,
	 * <code>null</code> if none.
	 */
	private IntroDescriptor introDescriptor;

	private IExtensionTracker tracker;

	private IRegistryChangeListener startupRegistryListener = new IRegistryChangeListener() {

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.core.runtime.IRegistryChangeListener#registryChanged(
		 * org.eclipse.core.runtime.IRegistryChangeEvent)
		 */
		public void registryChanged(IRegistryChangeEvent event) {
			final IExtensionDelta[] deltas = event.getExtensionDeltas(PlatformUI.PLUGIN_ID,
					IWorkbenchRegistryConstants.PL_STARTUP);
			if (deltas.length == 0) {
				return;
			}
			final String disabledPlugins = PrefUtil.getInternalPreferenceStore().getString(
					IPreferenceConstants.PLUGINS_NOT_ACTIVATED_ON_STARTUP);

			for (int i = 0; i < deltas.length; i++) {
				IExtension extension = deltas[i].getExtension();
				if (deltas[i].getKind() == IExtensionDelta.REMOVED) {
					continue;
				}

				// if the plugin is not in the set of disabled plugins,
				// then
				// execute the code to start it
				if (disabledPlugins.indexOf(extension.getNamespace()) == -1) {
					SafeRunner.run(new EarlyStartupRunnable(extension));
				}
			}

		}
	};

	private String factoryID;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getThemeManager()
	 */
	public IThemeManager getThemeManager() {
		return WorkbenchThemeManager.getInstance();
	}

	/**
	 * Returns <code>true</code> if the workbench is running, <code>false</code>
	 * if it has been terminated.
	 * 
	 * @return <code>true</code> if the workbench is running, <code>false</code>
	 *         if it has been terminated.
	 */
	public boolean isRunning() {
		return runEventLoop;
	}

	/**
	 * Return the presentation ID specified by the preference or the default ID
	 * if undefined.
	 * 
	 * @return the presentation ID
	 * @see IWorkbenchPreferenceConstants#PRESENTATION_FACTORY_ID
	 */
	public String getPresentationId() {
		if (factoryID != null) {
			return factoryID;
		}

		factoryID = PrefUtil.getAPIPreferenceStore().getString(
				IWorkbenchPreferenceConstants.PRESENTATION_FACTORY_ID);

		// Workaround for bug 58975 - New preference mechanism does not properly
		// initialize defaults
		// Ensure that the UI plugin has started too.
		if (factoryID == null || factoryID.equals("")) { //$NON-NLS-1$
			factoryID = IWorkbenchConstants.DEFAULT_PRESENTATION_ID;
		}
		return factoryID;
	}

	/**
	 * <p>
	 * Indicates the start of a large update within the workbench. This is used
	 * to disable CPU-intensive, change-sensitive services that were temporarily
	 * disabled in the midst of large changes. This method should always be
	 * called in tandem with <code>largeUpdateEnd</code>, and the event loop
	 * should not be allowed to spin before that method is called.
	 * </p>
	 * <p>
	 * Important: always use with <code>largeUpdateEnd</code>!
	 * </p>
	 */
	public final void largeUpdateStart() {
		if (largeUpdates++ == 0) {
			// TODO Consider whether these lines still need to be here.
			// workbenchCommandSupport.setProcessing(false);
			// workbenchContextSupport.setProcessing(false);

			final IWorkbenchWindow[] windows = getWorkbenchWindows();
			for (int i = 0; i < windows.length; i++) {
				IWorkbenchWindow window = windows[i];
				if (window instanceof WorkbenchWindow) {
					((WorkbenchWindow) window).largeUpdateStart();
				}
			}
		}
	}

	/**
	 * <p>
	 * Indicates the end of a large update within the workbench. This is used to
	 * re-enable services that were temporarily disabled in the midst of large
	 * changes. This method should always be called in tandem with
	 * <code>largeUpdateStart</code>, and the event loop should not be allowed
	 * to spin before this method is called.
	 * </p>
	 * <p>
	 * Important: always protect this call by using <code>finally</code>!
	 * </p>
	 */
	public final void largeUpdateEnd() {
		if (--largeUpdates == 0) {
			// TODO Consider whether these lines still need to be here.
			// workbenchCommandSupport.setProcessing(true);
			// workbenchContextSupport.setProcessing(true);

			// Perform window-specific blocking.
			final IWorkbenchWindow[] windows = getWorkbenchWindows();
			for (int i = 0; i < windows.length; i++) {
				IWorkbenchWindow window = windows[i];
				if (window instanceof WorkbenchWindow) {
					((WorkbenchWindow) window).largeUpdateEnd();
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getExtensionTracker()
	 */
	public IExtensionTracker getExtensionTracker() {
		return (IExtensionTracker) e4Context.get(IExtensionTracker.class.getName());
	}

	/**
	 * Adds the listener that handles startup plugins
	 * 
	 * @since 3.1
	 */
	private void addStartupRegistryListener() {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		registry.addRegistryChangeListener(startupRegistryListener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getHelpSystem()
	 */
	public IWorkbenchHelpSystem getHelpSystem() {
		return WorkbenchHelpSystem.getInstance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getHelpSystem()
	 */
	public IWorkbenchBrowserSupport getBrowserSupport() {
		return WorkbenchBrowserSupport.getInstance();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getViewRegistry()
	 */
	public IViewRegistry getViewRegistry() {
		return WorkbenchPlugin.getDefault().getViewRegistry();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getNewWizardRegistry()
	 */
	public IWizardRegistry getNewWizardRegistry() {
		return WorkbenchPlugin.getDefault().getNewWizardRegistry();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getImportWizardRegistry()
	 */
	public IWizardRegistry getImportWizardRegistry() {
		return WorkbenchPlugin.getDefault().getImportWizardRegistry();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getExportWizardRegistry()
	 */
	public IWizardRegistry getExportWizardRegistry() {
		return WorkbenchPlugin.getDefault().getExportWizardRegistry();
	}

	public final Object getAdapter(final Class key) {
		return serviceLocator.getService(key);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.services.IServiceLocator#getService(java.lang.Object)
	 */
	public final Object getService(final Class key) {
		return serviceLocator.getService(key);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.services.IServiceLocator#hasService(java.lang.Object)
	 */
	public final boolean hasService(final Class key) {
		return serviceLocator.hasService(key);
	}

	/**
	 * Registers a service with this locator. If there is an existing service
	 * matching the same <code>api</code> and it implements {@link IDisposable},
	 * it will be disposed.
	 * 
	 * @param api
	 *            This is the interface that the service implements. Must not be
	 *            <code>null</code>.
	 * @param service
	 *            The service to register. This must be some implementation of
	 *            <code>api</code>. This value must not be <code>null</code>.
	 */
	public final void registerService(final Class api, final Object service) {
		serviceLocator.registerService(api, service);
	}

	/**
	 * The source provider that tracks which context menus (i.e., menus with
	 * target identifiers) are now showing. This value is <code>null</code>
	 * until {@link #initializeDefaultServices()} is called.
	 */
	private MenuSourceProvider menuSourceProvider;

	/**
	 * Adds the ids of a menu that is now showing to the menu source provider.
	 * This is used for legacy action-based handlers which need to become active
	 * only for the duration of a menu being visible.
	 * 
	 * @param menuIds
	 *            The identifiers of the menu that is now showing; must not be
	 *            <code>null</code>.
	 * @param localSelection
	 * @param localEditorInput
	 */
	public final void addShowingMenus(final Set menuIds, final ISelection localSelection,
			final ISelection localEditorInput) {
		menuSourceProvider.addShowingMenus(menuIds, localSelection, localEditorInput);
		Map currentState = menuSourceProvider.getCurrentState();
		for (String key : menuSourceProvider.getProvidedSourceNames()) {
			e4Context.set(key, currentState.get(key));
		}
	}

	/**
	 * Removes the ids of a menu that is now hidden from the menu source
	 * provider. This is used for legacy action-based handlers which need to
	 * become active only for the duration of a menu being visible.
	 * 
	 * @param menuIds
	 *            The identifiers of the menu that is now hidden; must not be
	 *            <code>null</code>.
	 * @param localSelection
	 * @param localEditorInput
	 */
	public final void removeShowingMenus(final Set menuIds, final ISelection localSelection,
			final ISelection localEditorInput) {
		menuSourceProvider.removeShowingMenus(menuIds, localSelection, localEditorInput);
		for (String key : menuSourceProvider.getProvidedSourceNames()) {
			e4Context.remove(key);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IWorkbench#saveAll(org.eclipse.jface.window.IShellProvider
	 * , org.eclipse.jface.operation.IRunnableContext,
	 * org.eclipse.ui.ISaveableFilter, boolean)
	 */
	public boolean saveAll(final IShellProvider shellProvider,
			final IRunnableContext runnableContext, final ISaveableFilter filter, boolean confirm) {
		Map<Saveable, Set<IWorkbenchPart>> map = new HashMap<Saveable, Set<IWorkbenchPart>>();

		for (IWorkbenchWindow window : getWorkbenchWindows()) {
			IWorkbenchPage page = window.getActivePage();

			IViewReference[] viewReferences = page.getViewReferences();
			for (IWorkbenchPartReference reference : viewReferences) {
				IWorkbenchPart part = reference.getPart(false);
				if (part instanceof ISaveablesSource) {
					Saveable[] saveables = ((ISaveablesSource) part).getSaveables();
					for (Saveable saveable : saveables) {
						if (saveable.isDirty()) {
							Set<IWorkbenchPart> parts = map.get(saveable);
							if (parts == null) {
								parts = new HashSet<IWorkbenchPart>();
								map.put(saveable, parts);
							}
							parts.add(part);
						}
					}
				}
			}

			IEditorReference[] editorReferences = page.getEditorReferences();
			for (IWorkbenchPartReference reference : editorReferences) {
				IWorkbenchPart part = reference.getPart(false);
				if (part instanceof ISaveablesSource) {
					Saveable[] saveables = ((ISaveablesSource) part).getSaveables();
					for (Saveable saveable : saveables) {
						if (saveable.isDirty()) {
							Set<IWorkbenchPart> parts = map.get(saveable);
							if (parts == null) {
								parts = new HashSet<IWorkbenchPart>();
								map.put(saveable, parts);
							}
							parts.add(part);
						}
					}
				}
			}
		}

		final List<Saveable> toSave = new ArrayList<Saveable>();

		for (Entry<Saveable, Set<IWorkbenchPart>> entrySet : map.entrySet()) {
			Saveable saveable = entrySet.getKey();
			Set<IWorkbenchPart> parts = entrySet.getValue();
			if (filter.select(saveable, parts.toArray(new IWorkbenchPart[parts.size()]))) {
				toSave.add(saveable);
			}
		}

		if (toSave.isEmpty()) {
			return true;
		}

		SaveablesList saveablesList = (SaveablesList) getService(ISaveablesLifecycleListener.class);

		if (!confirm) {
			return !saveablesList.saveModels(toSave, shellProvider, runnableContext);
		}

		// We must negate the result since false is cancel saveAll
		return !saveablesList.promptForSaving(toSave, shellProvider, runnableContext, true, false);
	}

	public ServiceLocator getServiceLocator() {
		return serviceLocator;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbench#getModalDialogShellProvider()
	 */
	public IShellProvider getModalDialogShellProvider() {
		return new IShellProvider() {
			public Shell getShell() {
				return ProgressManagerUtil.getDefaultParent();
			}
		};
	}

	public IEclipseContext getContext() {
		return e4Context;
	}

	public MApplication getApplication() {
		return application;
	}
}
