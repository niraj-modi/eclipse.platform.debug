package org.eclipse.ui.externaltools.internal.ui;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others.
All rights reserved.   This program and the accompanying materials
are made available under the terms of the Common Public License v0.5
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v05.html
 
Contributors:
**********************************************************************/
import java.util.*;

import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.*;
import org.eclipse.jface.util.*;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.externaltools.internal.core.*;
import org.eclipse.ui.externaltools.internal.model.*;

/**
 * Holds onto messages generated by the execution on an
 * external tool.
 */
public class LogConsoleDocument {
	// class variables that handle the colors and the font
	private static Color ERROR_COLOR;
	private static Color WARN_COLOR;
	private static Color INFO_COLOR;
	private static Color VERBOSE_COLOR;
	private static Color DEBUG_COLOR;
	/*package*/ static Font ANT_FONT;
	
	public static final int MSG_ERR = 0;
	public static final int MSG_WARN = 10;
	public static final int MSG_INFO = 20;
	public static final int MSG_VERBOSE = 30;
	public static final int MSG_DEBUG = 40;
	
	private static final LogConsoleDocument instance = new LogConsoleDocument();
	
	private LogPropertyChangeListener changeListener;

	/*package*/ ArrayList views = new ArrayList();
	private Document document;
	private ArrayList styleRanges;
	
	// Structure to store the textwidget index information
	private OutputStructureElement root = null;	
	private OutputStructureElement currentElement = null;

	private LogConsoleDocument() {
		changeListener = new LogPropertyChangeListener();
		document = new Document();
		styleRanges = new ArrayList(5);
		initializeOutputStructure();		
	}

	public void append(final String message, final int priority) {
		if (views.size() == 0)
			return;
		((LogConsoleView)views.get(0)).getViewSite().getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				int start = getDocument().getLength();
				try {
					// Append new message to the end of the document. This
					// avoids console flicker when messages are appended.
					getDocument().replace(start, 0, message);
				} catch (BadLocationException e) {
				}
				setOutputLevelColor(priority, start, message.length());
			}
		});		
		for (int i=0; i < views.size(); i++) {
			((LogConsoleView)views.get(i)).append(message, priority);	
		}
	}
	
	private void addRangeStyle(int start, int length, Color color) {
		// Don't add a StyleRange if the length is 0.
		if (length == 0)
			return;	
		if (styleRanges.size() != 0) {
			StyleRange lastStyle = (StyleRange) styleRanges.get(styleRanges.size()-1);
			if (color.equals(lastStyle.foreground))
				lastStyle.length += length;
			else
				styleRanges.add(new StyleRange(start, length, color, null));
		} else
			styleRanges.add(new StyleRange(start, length, color, null));
		StyleRange[] styleArray = (StyleRange[]) styleRanges.toArray(new StyleRange[styleRanges.size()]);			
		for (int i = 0; i < views.size(); i++) {
			TextViewer tv = ((LogConsoleView)views.get(i)).getTextViewer();
			if (tv != null)
				tv.getTextWidget().setStyleRanges(styleArray);
		}
	}
	
	public void clearOutput() {
		document.set("");
		styleRanges.clear();
		// the tree can be null if #createPartControl has not called yet, 
		// i.e. if the console exists but has never been shown so far
		initializeOutputStructure();
		refreshTree();
	}	

	public void refreshTree() {
		for(int i=0; i<views.size(); i++) {
			((LogConsoleView)views.get(i)).refreshTree();
		}
	}	
	
	/**
	 * Returns the color used for error messages on the log console.
	 */
	private static Color getErrorColor() {
		if (ERROR_COLOR == null || ERROR_COLOR.isDisposed())
			ERROR_COLOR = new Color(null, PreferenceConverter.getColor(ExternalToolsPlugin.getDefault().getPreferenceStore(),IPreferenceConstants.CONSOLE_ERROR_RGB));
		return ERROR_COLOR;
	}
	/**
	 * Returns the color used for warning messages on the log console.
	 */
	private static Color getWarnColor() {
		if (WARN_COLOR == null || WARN_COLOR.isDisposed())
			WARN_COLOR = new Color(null, PreferenceConverter.getColor(ExternalToolsPlugin.getDefault().getPreferenceStore(),IPreferenceConstants.CONSOLE_WARNING_RGB));	
		return WARN_COLOR;
	}
	/**
	 * Returns the color used for info (normal) messages on the log console.
	 */
	private static Color getInfoColor() {
		if (INFO_COLOR == null || INFO_COLOR.isDisposed())
			INFO_COLOR = new Color(null, PreferenceConverter.getColor(ExternalToolsPlugin.getDefault().getPreferenceStore(),IPreferenceConstants.CONSOLE_INFO_RGB));		
		return INFO_COLOR;
	}
	/**
	 * Returns the color used for verbose messages on the log console.
	 */
	private static Color getVerboseColor() {
		if (VERBOSE_COLOR == null || VERBOSE_COLOR.isDisposed())
			VERBOSE_COLOR = new Color(null, PreferenceConverter.getColor(ExternalToolsPlugin.getDefault().getPreferenceStore(),IPreferenceConstants.CONSOLE_VERBOSE_RGB));		
		return VERBOSE_COLOR;
	}
	/**
	 * Returns the color used for debug messages on the log console.
	 */
	private static Color getDebugColor() {
		if (DEBUG_COLOR == null || DEBUG_COLOR.isDisposed())
			DEBUG_COLOR = new Color(null, PreferenceConverter.getColor(ExternalToolsPlugin.getDefault().getPreferenceStore(),IPreferenceConstants.CONSOLE_DEBUG_RGB));	
		return DEBUG_COLOR;
	}
	
	public Display getDisplay() {
		if (!hasViews()) 
			return null;
		return ((LogConsoleView)views.get(0)).getSite().getShell().getDisplay();	
	}
	
	/*package*/ Document getDocument() {
		return document;	
	}
	
	/*package*/  ArrayList getStyleRanges() {
		return styleRanges;	
	}
	
	public ArrayList getViews() {
		return views;
	}
	
	/*package*/ OutputStructureElement getRoot() {
		return root;	
	}
	
	public boolean hasViews() {
		return (views.size() > 0);	
	}
	
	public void initializeOutputStructure() {
		// root is the first element of the structure: it is a fake so it doesn't need a real name
		root = new OutputStructureElement("-- root --"); // $NON-NLS-1$
		currentElement = new OutputStructureElement(ToolMessages.getString("LogConsoleDocument.externalTool"), root, 0); // $NON-NLS-1$
		
		for (int i=0; i < views.size(); i++) {
			LogConsoleView view = (LogConsoleView)views.get(i);
			if (view.getTreeViewer() != null)
				view.initializeTreeInput();
		}
	}
	
	public void registerView(LogConsoleView view) {
		if (!hasViews()) {
			// first time there is an instance of this class: intantiate the font and register the listener
			ANT_FONT = new Font(null, PreferenceConverter.getFontData(ExternalToolsPlugin.getDefault().getPreferenceStore(),IPreferenceConstants.CONSOLE_FONT));
			ExternalToolsPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(changeListener);
		}
		views.add(view);	
	}
	
	public void unregisterView(LogConsoleView view) {
		views.remove(view);	
		if (! hasViews()) {
			// all the consoles are diposed: we can dispose the font and remove the property listener
			ANT_FONT.dispose();
			ExternalToolsPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(changeListener);
		}
	}
	
	public static LogConsoleDocument getInstance() {
		return instance;	
	}
	
	public OutputStructureElement getCurrentOutputStructureElement() {
		return currentElement;
	}
	public void setCurrentOutputStructureElement(OutputStructureElement output) {
		this.currentElement = output;
	}
	/*package*/ void setOutputLevelColor(int level, int start, int end) {
		switch (level) {
			case LogConsoleDocument.MSG_ERR: 
				addRangeStyle(start, end, getErrorColor()); 
				break;
			case LogConsoleDocument.MSG_WARN: 
				addRangeStyle(start, end, getWarnColor()); 
				break;
			case LogConsoleDocument.MSG_INFO: 
				addRangeStyle(start, end, getInfoColor()); 
				break;
			case LogConsoleDocument.MSG_VERBOSE: 
				addRangeStyle(start, end, getVerboseColor()); 
				break;
			case LogConsoleDocument.MSG_DEBUG: 
				addRangeStyle(start, end, getDebugColor()); 
				break;
			default: 
				addRangeStyle(start, end, getInfoColor());
		}
	}
	
	/**
	 * Replaces the old color with the new one in all style ranges,
	 */
	private void updateStyleRanges(Color oldColor, Color newColor) {
		for (int i=0; i<styleRanges.size(); i++) {
			StyleRange range = (StyleRange)styleRanges.get(i);
			if (range.foreground == oldColor)
				range.foreground = newColor;
		}
	}

	private class LogPropertyChangeListener implements IPropertyChangeListener {
	
		// private constructor to ensure the singleton
		private LogPropertyChangeListener() {
		}
		
		/**
		 * @see IPropertyChangeListener#propertyChange(PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {
				String propertyName= event.getProperty();
			
				if (propertyName.equals(IPreferenceConstants.CONSOLE_ERROR_RGB)) {
					if (LogConsoleDocument.ERROR_COLOR == null)
						return;
					Color temp = getErrorColor();
					LogConsoleDocument.ERROR_COLOR = ToolsPreferencePage.getPreferenceColor(IPreferenceConstants.CONSOLE_ERROR_RGB);
					updateStyleRanges(temp, getErrorColor());
					temp.dispose();
				} else if (propertyName.equals(IPreferenceConstants.CONSOLE_WARNING_RGB)) {
					if (LogConsoleDocument.WARN_COLOR == null)
						return;
					Color temp = getWarnColor();
					LogConsoleDocument.WARN_COLOR = ToolsPreferencePage.getPreferenceColor(IPreferenceConstants.CONSOLE_WARNING_RGB);
					updateStyleRanges(temp, getWarnColor());
					temp.dispose();
				} else if (propertyName.equals(IPreferenceConstants.CONSOLE_INFO_RGB)) {
					if (LogConsoleDocument.INFO_COLOR == null)
						return;
					Color temp = getInfoColor();
					LogConsoleDocument.INFO_COLOR = ToolsPreferencePage.getPreferenceColor(IPreferenceConstants.CONSOLE_INFO_RGB);
					updateStyleRanges(temp, getInfoColor());
					temp.dispose();
				} else if (propertyName.equals(IPreferenceConstants.CONSOLE_VERBOSE_RGB)) {
					if (LogConsoleDocument.VERBOSE_COLOR == null)
						return;
					Color temp = getVerboseColor();
					LogConsoleDocument.VERBOSE_COLOR = ToolsPreferencePage.getPreferenceColor(IPreferenceConstants.CONSOLE_VERBOSE_RGB);
					updateStyleRanges(temp, getVerboseColor());
					temp.dispose();
				} else if (propertyName.equals(IPreferenceConstants.CONSOLE_DEBUG_RGB)) {
					if (LogConsoleDocument.DEBUG_COLOR == null)
						return;
					Color temp = getDebugColor();
					LogConsoleDocument.DEBUG_COLOR = ToolsPreferencePage.getPreferenceColor(IPreferenceConstants.CONSOLE_DEBUG_RGB);
					updateStyleRanges(temp, getDebugColor());
					temp.dispose();
				} else if (propertyName.equals(IPreferenceConstants.CONSOLE_FONT)) {
					FontData data= ToolsPreferencePage.getConsoleFontData();
					Font temp= LogConsoleDocument.ANT_FONT;
					LogConsoleDocument.ANT_FONT = new Font(Display.getCurrent(), data);
					temp.dispose();
					updateFont();	
				} else
					return;
		}
	
		/**
		 * Clears the output of all the consoles
		 */
		private void clearOutput() {
			LogConsoleDocument.getInstance().clearOutput();
		}
		
		/**
		 * Updates teh font in all the consoles
		 */
		private void updateFont() {
			for (Iterator iterator = LogConsoleDocument.getInstance().getViews().iterator(); iterator.hasNext();)
				 ((LogConsoleView) iterator.next()).updateFont();
		}
	}


}
