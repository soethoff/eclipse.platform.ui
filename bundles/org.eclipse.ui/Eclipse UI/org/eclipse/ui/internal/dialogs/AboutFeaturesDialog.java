package org.eclipse.ui.internal.dialogs;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.internal.AboutInfo;
import org.eclipse.ui.internal.AboutItem;
import org.eclipse.ui.internal.IHelpContextIds;
import org.eclipse.ui.internal.ProductInfo;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.WorkbenchMessages;

/**
 * Displays information about the product plugins.
 *
 * @private
 *		This class is internal to the workbench and must not be called outside the workbench
 */
public class AboutFeaturesDialog extends Dialog {
	
	private static final String ATT_HTTP = "http://"; //$NON-NLS-1$

	/**
	 * Table height in dialog units (value 150).
	 */
	private static final int TABLE_HEIGHT = 150;
	private static final int INFO_HEIGHT = 100;

	private static final String PLUGININFO = "about.html";	//$NON-NLS-1$

	private boolean webBrowserOpened = false;

	private Table table;
	private Label imageLabel;	
	private StyledText text;
	private Composite infoArea;
	
	private Map cachedImages = new HashMap();

	private String columnTitles[] =
		{ WorkbenchMessages.getString("AboutFeaturesDialog.provider"), //$NON-NLS-1$
		WorkbenchMessages.getString("AboutFeaturesDialog.featureName"), //$NON-NLS-1$
		WorkbenchMessages.getString("AboutFeaturesDialog.version"), //$NON-NLS-1$
	};

	private AboutInfo[] featuresInfo;
	private AboutInfo aboutInfo;
	private ProductInfo productInfo;
	
	private int lastColumnChosen = 0;	// initially sort by provider
	private boolean reverseSort = false;	// initially sort ascending
	private AboutInfo lastSelection = null;

	private 	AboutItem item;

	private    int ABOUT_TEXT_WIDTH = 70; // chars
	private    int ABOUT_TEXT_HEIGHT = 15; // chars
	private 	Cursor handCursor;
	private 	Cursor busyCursor;
	
	/**
	 * Constructor for AboutFeaturesDialog
	 */
	public AboutFeaturesDialog(Shell parentShell) {
		super(parentShell);
		Workbench workbench = (Workbench)PlatformUI.getWorkbench();
		aboutInfo = workbench.getAboutInfo();
		productInfo = workbench.getProductInfo();
		featuresInfo = workbench.getFeaturesInfo();
		sortByProvider();
	}

	/* (non-Javadoc)
	 * Method declared on Window.
	 */
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		String title = aboutInfo.getAppName();
		if (title ==  null) {
			// backward compatibility
			title = productInfo.getName();
		}
		newShell.setText(
			WorkbenchMessages.format(
				"AboutFeaturesDialog.shellTitle",	//$NON-NLS-1$
				new Object[] {title}));
		WorkbenchHelp.setHelp(
			newShell,
			IHelpContextIds.ABOUT_FEATURES_DIALOG);
	} 
	/**
	 * Add buttons to the dialog's button bar.
	 *
	 * Subclasses should override.
	 *
	 * @param parent the button bar composite
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
	}
	/**
	 * Create the contents of the dialog (above the button bar).
	 *
	 * Subclasses should overide.
	 *
	 * @param the parent composite to contain the dialog area
	 * @return the dialog area control
	 */
	protected Control createDialogArea(Composite parent) {
		handCursor = new Cursor(parent.getDisplay(), SWT.CURSOR_HAND);
		busyCursor = new Cursor(parent.getDisplay(), SWT.CURSOR_WAIT);
		getShell().addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				handCursor.dispose();
				busyCursor.dispose();
			}
		});

		Composite outer = (Composite) super.createDialogArea(parent);

		createTable(outer);
		createColumns();
		createInfoArea(outer);

		GridData gridData =
			new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL);
		gridData.grabExcessVerticalSpace = true;
		gridData.grabExcessHorizontalSpace = true;

		// suggest a height for the table
		gridData.heightHint = convertVerticalDLUsToPixels(TABLE_HEIGHT);
		table.setLayoutData(gridData);

		return outer;
	}
	/** 
	 * Create the info area containing the image and text
	 */
	protected void createInfoArea(Composite parent) {
		infoArea = new Composite(parent, SWT.NULL);
		GridLayout layout= new GridLayout();
		layout.numColumns = 2; 
		infoArea.setLayout(layout);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.heightHint = convertVerticalDLUsToPixels(INFO_HEIGHT);
		infoArea.setLayoutData(data);
		
		imageLabel = new Label(infoArea, SWT.NONE);
		data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.BEGINNING;
		data.heightHint = 32;
		data.widthHint = 32;
		imageLabel.setLayoutData(data);
		
		// text on the right
		text = new StyledText(infoArea, SWT.MULTI | SWT.READ_ONLY);
		text.setCaret(null);
		text.setFont(parent.getFont());
		data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.BEGINNING;
		data.grabExcessHorizontalSpace = true;
		data.widthHint = convertWidthInCharsToPixels(ABOUT_TEXT_WIDTH);
		data.heightHint = convertHeightInCharsToPixels(ABOUT_TEXT_HEIGHT);
		text.setLayoutData(data);
		text.setCursor(null);
		text.setBackground(infoArea.getBackground());
		addListeners(text);
		
		TableItem[] items = table.getSelection();
		if (items.length > 0) 
			updateInfoArea((AboutInfo)items[0].getData());
	}		
	
	/**
	 * Create the table part of the dialog.
	 *
	 * @param the parent composite to contain the dialog area
	 */
	protected void createTable(Composite parent) {
		table =
			new Table(
				parent,
				SWT.H_SCROLL | SWT.V_SCROLL | SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		SelectionListener listener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateInfoArea((AboutInfo)e.item.getData());
			}
		};
		table.addSelectionListener(listener);
	}
	/**
	 * @see Window#close()
	 */
	public boolean close() {
		Collection values = cachedImages.values();
		for (Iterator iter = values.iterator(); iter.hasNext();) {
			Image image = (Image) iter.next();
			image.dispose();
		}
		return super.close();
	}

	/**
	 * Update the info area
	 */
	private void updateInfoArea(AboutInfo info) {
		if (info == null) {
			imageLabel.setImage(null);
			text.setText(""); //$NON-NLS-1$
			return;
		}
		ImageDescriptor desc = info.getFeatureImage();
		Image image =  (Image)cachedImages.get(desc);
		if (image == null && desc != null) {
			image = desc.createImage();
			cachedImages.put(desc, image);
		}
		imageLabel.setImage(image);
		String aboutText = info.getAboutText();
		item = null;
		if (aboutText != null) {
			// get an about item
			item = scan(aboutText);
		}
		if (item == null)
			text.setText(WorkbenchMessages.getString("AboutFeaturesDialog.noInformation"));
		else {
			text.setText(item.getText());	
			text.setCursor(null);
			setLinkRanges(text, item.getLinkRanges());
		}
	}
	
	/**
	 * Scan the contents of the about text
	 */
	private AboutItem scan(String s) {
		int max = s.length();
		int i = s.indexOf(ATT_HTTP);
		ArrayList linkRanges = new ArrayList();
		ArrayList links = new ArrayList();
		while (i != -1) {
			int start = i;
			// look for the first whitespace character
			boolean found = false;
			i += ATT_HTTP.length();
			while (!found && i < max) {
				found = Character.isWhitespace(s.charAt(i++));
			}
			linkRanges.add(new int[] {start, i - start});
			links.add(s.substring(start, i));
			i = s.indexOf(ATT_HTTP, i);
		}
		return new AboutItem(
				s,
				(int[][])linkRanges.toArray(new int[linkRanges.size()][2]),
				(String[])links.toArray(new String[links.size()]));
	}
	
	/** 
	 * Select the initial selection
	 * 
	 */
	public void setInitialSelection(AboutInfo info) {
		lastSelection = info;
	}	
	
	/**
	 * Populate the table with plugin info obtained from the registry.
	 *
	 * @param the parent composite to contain the dialog area
	 */
	protected void createColumns() {
		/* create table headers */
		int[] columnWidths =
			{
				convertHorizontalDLUsToPixels(165),
				convertHorizontalDLUsToPixels(165),
				convertHorizontalDLUsToPixels(50)};
		for (int i = 0; i < columnTitles.length; i++) {
			TableColumn tableColumn = new TableColumn(table, SWT.NULL);
			tableColumn.setWidth(columnWidths[i]);
			tableColumn.setText(columnTitles[i]);
			final int columnIndex = i;
			tableColumn.addSelectionListener(new SelectionAdapter() {		
				public void widgetSelected(SelectionEvent e) {
					sort(columnIndex);
				}
			});
		
		}

		int initialSelectionIndex = 0;
		/* fill each row of the table with feature info */
		for (int i = 0; i < featuresInfo.length; i++) {
			if (featuresInfo[i] == lastSelection)
				initialSelectionIndex = i;
			String provider = featuresInfo[i].getProviderName();
			String featureName = featuresInfo[i].getFeatureLabel();
			String version = featuresInfo[i].getVersion();
			if (provider == null)
				provider = "";
			if (featureName == null)
				featureName = "";
			if (version == null)
				version = "";
			String[] row = { provider, featureName, version };
			TableItem item = new TableItem(table, SWT.NULL);
			item.setText(row);
			item.setData(featuresInfo[i]);
		}
		
		// set initial selection
		if (featuresInfo.length > 0) {
			table.setSelection(initialSelectionIndex);
		}
	}

	
	/**
	 * Sort the rows of the table based on the selected column.
	 *
	 * @param column index of table column selected as sort criteria
	 */
	private void sort(int column) {
		// Choose new sort algorithm
		if (lastColumnChosen == column){
			reverseSort = !reverseSort;
		}
		else{
			reverseSort = false;
			lastColumnChosen = column;
		}
		
		if(table.getItemCount() <= 1)	return;

		// Remember the last selection
		int idx = table.getSelectionIndex();
		if (idx != -1)
			lastSelection = featuresInfo[idx];
			
		switch (column){
			case 0:
				sortByProvider();
				break;
			case 1:
				sortByName();
				break;
			case 2:
				sortByVersion();
				break;
		}

		refreshTable(column);
	}

	/**
	 * Refresh the rows of the table based on the selected column.
	 * Maintain selection from before sort action request.
	 *
	 * @param items the old state table items 
	 */
	private void refreshTable(int col){
		TableItem[] items = table.getItems();
		int idx = -1;	// the new index of the selection
		// Create new order of table items
		for(int i = 0; i < items.length; i++) {
			String provider = featuresInfo[i].getProviderName();
			String featureName = featuresInfo[i].getFeatureLabel();
			String version = featuresInfo[i].getVersion();
			if (provider == null)
				provider = "";
			if (featureName == null)
				featureName = "";
			if (version == null)
				version = "";
			String[] row = { provider, featureName, version };
			items[i].setText(row);
			items[i].setData(featuresInfo[i]);
		}
		// Maintain the original selection
		if (lastSelection != null){
			for (int k = 0; k < featuresInfo.length; k++){
				if (lastSelection == featuresInfo[k])
					idx = k;
			}	
			table.setSelection(idx);
			table.showSelection();
		}

		updateInfoArea(lastSelection);
	}
	/**
	 * Sort the rows of the table based on the plugin provider.
	 * Secondary criteria is unique plugin id.
	 */
	private void sortByProvider(){
		/* If sorting in reverse, info array is already sorted forward by
		 * key so the info array simply needs to be reversed.
		 */
		if (reverseSort){
			java.util.List infoList = Arrays.asList(featuresInfo);
			Collections.reverse(infoList);
			for (int i=0; i< featuresInfo.length; i++){
				featuresInfo[i] = (AboutInfo)infoList.get(i);
			}
		}
		else {
			// Sort ascending
			Arrays.sort(featuresInfo, new Comparator() {
				Collator coll = Collator.getInstance(Locale.getDefault());
				public int compare(Object a, Object b) {
					AboutInfo i1, i2;
					String provider1, provider2, name1, name2;
					i1 = (AboutInfo)a;
					provider1 = i1.getProviderName();
					name1 = i1.getProductName();
					if (provider1 == null)
						provider1 = "";
					if (name1 == null)
						name1 = "";
					i2 = (AboutInfo)b;
					provider2 = i2.getProviderName();
					name2 = i2.getProductName();
					if (provider2 == null)
						provider2 = "";
					if (name2 == null)
						name2 = "";
					if (provider1.equals(provider2))
						return coll.compare(name1, name2);
					else
						return coll.compare(provider1, provider2);
				}
			});
		}
	}
	/**
	 * Sort the rows of the table based on unique plugin id.
	 */	
	private void sortByName(){
		/* If sorting in reverse, info array is already sorted forward by
		 * key so the info array simply needs to be reversed.
		 */
		if (reverseSort){
			java.util.List infoList = Arrays.asList(featuresInfo);
			Collections.reverse(infoList);
			for (int i=0; i< featuresInfo.length; i++){
				featuresInfo[i] = (AboutInfo)infoList.get(i);
			}
		}
		else {
			// Sort ascending
			Arrays.sort(featuresInfo, new Comparator() {
				Collator coll = Collator.getInstance(Locale.getDefault());
				public int compare(Object a, Object b) {
					AboutInfo i1, i2;
					String name1, name2;
					i1 = (AboutInfo)a;
					name1 = i1.getProductName();
					i2 = (AboutInfo)b;
					name2 = i2.getProductName();
					if (name1 == null)
						name1 = "";
					if (name2 == null)
						name2 = "";
					return coll.compare(name1, name2);
				}
			});
		}
	
	}
	/**
	 * Sort the rows of the table based on the plugin version.
	 * Secondary criteria is unique plugin id.
	 */
	private void sortByVersion(){
		/* If sorting in reverse, info array is already sorted forward by
		 * key so the info array simply needs to be reversed.
		 */		
		if (reverseSort){
			java.util.List infoList = Arrays.asList(featuresInfo);
			Collections.reverse(infoList);
			for (int i=0; i< featuresInfo.length; i++){
				featuresInfo[i] = (AboutInfo)infoList.get(i);
			}
		}
		else {
			// Sort ascending
			Arrays.sort(featuresInfo, new Comparator() {
				Collator coll = Collator.getInstance(Locale.getDefault());
				public int compare(Object a, Object b) {
					AboutInfo i1, i2;
					String version1, version2, name1, name2;
					i1 = (AboutInfo)a;
					version1 = i1.getVersion();
					name1 = i1.getProductName();
					if (version1 == null)
						version1 = "";
					if (name1 == null)
						name1 = "";
					i2 = (AboutInfo)b;
					version2 = i2.getVersion();
					name2 = i2.getProductName();
					if (version2 == null)
						version2 = "";
					if (name2 == null)
						name2 = "";
					if (version1.equals(version2))
						return coll.compare(name1, name2);
					else
						return coll.compare(version1, version2);
				}
			});
		}
	}
	
/**
 * Adds listeners to the given styled text
 */
private void addListeners(StyledText styledText) {
	styledText.addMouseListener(new MouseAdapter() {
		public void mouseUp(MouseEvent e) {
			StyledText text = (StyledText)e.widget;
			int offset = text.getCaretOffset();
			if (item != null && item.isLinkAt(offset)) {	
				text.setCursor(busyCursor);
				openLink(item.getLinkAt(offset));
				text.setCursor(null);
			}
		}
	});
	styledText.addMouseMoveListener(new MouseMoveListener() {
		public void mouseMove(MouseEvent e) {
			StyledText text = (StyledText)e.widget;
			int offset = -1;
			try {
				offset = text.getOffsetAtLocation(new Point(e.x, e.y));
			} catch (IllegalArgumentException ex) {
				// leave value as -1
			}
			if (offset == -1)
				text.setCursor(null);
			else if (item != null && item.isLinkAt(offset)) 
				text.setCursor(handCursor);
			else 
				text.setCursor(null);
		}
	});
}
	/**
	 * Open a link
	 */
	private void openLink(final String href) {
		if (SWT.getPlatform().equals("win32")) { //$NON-NLS-1$
			Program.launch(href);
		} else {
				Thread launcher = new Thread("About Link Launcher") {//$NON-NLS-1$
	public void run() {
					try {
						if (webBrowserOpened) {
							Runtime.getRuntime().exec("netscape -remote openURL(" + href + ")"); //$NON-NLS-1$ //$NON-NLS-2$
						} else {
							Process p = Runtime.getRuntime().exec("netscape " + href); //$NON-NLS-1$
							webBrowserOpened = true;
							try {
								if (p != null)
									p.waitFor();
							} catch (InterruptedException e) {
								MessageDialog.openError(AboutFeaturesDialog.this.getShell(), WorkbenchMessages.getString("AboutDialog.errorTitle"), //$NON-NLS-1$
								e.getMessage());
							} finally {
								webBrowserOpened = false;
							}
						}
					} catch (IOException e) {
						MessageDialog.openError(AboutFeaturesDialog.this.getShell(), WorkbenchMessages.getString("AboutDialog.errorTitle"), //$NON-NLS-1$
						e.getMessage());

					}
				}
			};
			launcher.start();
		}
	}

	/**
	 * Sets the styled text's bold ranges
	 */
	private void setBoldRanges(StyledText styledText, int[][] boldRanges) {
		for (int i = 0; i < boldRanges.length; i++) {
			StyleRange r =
				new StyleRange(boldRanges[i][0], boldRanges[i][1], null, null, SWT.BOLD);
			styledText.setStyleRange(r);
		}
	}
	/**
	 * Sets the styled text's link (blue) ranges
	 */
	private void setLinkRanges(StyledText styledText, int[][] linkRanges) {
		Color fg =
			JFaceColors.getHyperlinkText(styledText.getShell().getDisplay());
		for (int i = 0; i < linkRanges.length; i++) {
			StyleRange r =
				new StyleRange(linkRanges[i][0], linkRanges[i][1], fg, null);
			styledText.setStyleRange(r);
		}
	}

}