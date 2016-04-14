package inra.ijpb.plugins;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.plugin.PlugIn;
import inra.ijpb.label.LabelImages;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel3D;

public class LabelEdition implements PlugIn 
{			
	/** main GUI window */
	private CustomWindow win;
	
	/** original input image */
	ImagePlus inputImage = null;
	/** copy of the input image stack */
	ImageStack inputStackCopy = null;

	/** image to be displayed in the GUI */
	ImagePlus displayImage = null;
	
	/** flag to indicate 2D input image */
	boolean inputIs2D = false;
	
	/** executor service to launch threads for the plugin methods and events */
	final ExecutorService exec = Executors.newFixedThreadPool( 1 );

	// Button panel components
	JButton mergeButton = null;
	JButton dilateButton = null;
	JButton erodeButton = null;
	JButton exitButton = null;	
	
	JPanel buttonsPanel = new JPanel();
	
	/** main panel */
	Panel all = new Panel();
	
	/**
	 * Custom window to define the plugin GUI
	 */
	private class CustomWindow extends StackWindow
	{

		/**
		 * Generated serial version UID
		 */
		private static final long serialVersionUID = 7356632113911531536L;

		/**
		 * Listener for the GUI buttons
		 */
		private ActionListener listener = new ActionListener() {

			@Override
			public void actionPerformed( final ActionEvent e )
			{
				// listen to the buttons on separate threads not to block
				// the event dispatch thread
				exec.submit(new Runnable() {

					public void run()
					{
						// Merge button
						if( e.getSource() == mergeButton )
						{
							mergeLabels( );
						}
						else if( e.getSource() == dilateButton )
						{
							dilateLabels( );
						}
					}
				});
			}
		};

		/**
		 * Custom window to create plugin GUI
		 * @param imp input label image (2d or 3d)
		 */
		public CustomWindow( ImagePlus imp ) 
		{
		
			super(imp, new ImageCanvas(imp));

			final ImageCanvas canvas = (ImageCanvas) getCanvas();

			// Zoom in if image is too small
			while( ic.getWidth() < 512 && ic.getHeight() < 512 )
				IJ.run( imp, "In","" );

			setTitle( "Label Edition" );

			mergeButton = new JButton( "Merge" );
			mergeButton.addActionListener( listener );

			dilateButton = new JButton( "Dilate" );
			dilateButton.addActionListener( listener );

			erodeButton = new JButton( "Erode" );
			erodeButton.addActionListener( listener );

			exitButton = new JButton( "Exit" );
			exitButton.addActionListener( listener );
			
			// Training panel (left side of the GUI)
			buttonsPanel.setBorder(
					BorderFactory.createTitledBorder( "Options" ) );
			GridBagLayout buttonsLayout = new GridBagLayout();
			GridBagConstraints buttonsConstraints = new GridBagConstraints();
			buttonsConstraints.anchor = GridBagConstraints.NORTHWEST;
			buttonsConstraints.fill = GridBagConstraints.HORIZONTAL;
			buttonsConstraints.gridwidth = 1;
			buttonsConstraints.gridheight = 1;
			buttonsConstraints.gridx = 0;
			buttonsConstraints.gridy = 0;
			buttonsConstraints.insets = new Insets( 5, 5, 6, 6 );
			buttonsPanel.setLayout( buttonsLayout );

			buttonsPanel.add( mergeButton, buttonsConstraints );
			buttonsConstraints.gridy++;
			buttonsPanel.add( dilateButton, buttonsConstraints );
			buttonsConstraints.gridy++;
			buttonsPanel.add( erodeButton, buttonsConstraints);
			buttonsConstraints.gridy++;
			buttonsPanel.add( exitButton, buttonsConstraints );
			buttonsConstraints.gridy++;
			
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints allConstraints = new GridBagConstraints();
			all.setLayout(layout);

			allConstraints.anchor = GridBagConstraints.NORTHWEST;
			allConstraints.fill = GridBagConstraints.BOTH;
			allConstraints.gridwidth = 1;
			allConstraints.gridheight = 2;
			allConstraints.gridx = 0;
			allConstraints.gridy = 0;
			allConstraints.weightx = 0;
			allConstraints.weighty = 0;

			all.add( buttonsPanel, allConstraints );
			
			allConstraints.gridx++;
			allConstraints.weightx = 1;
			allConstraints.weighty = 1;
			allConstraints.gridheight = 1;
			all.add( canvas, allConstraints );
			
			allConstraints.gridy++;
			allConstraints.weightx = 0;
			allConstraints.weighty = 0;
			// if the input image is 3d, put the
			// slice selectors in place
			if( null != super.sliceSelector )
			{
				sliceSelector.setValue( inputImage.getCurrentSlice() );
				displayImage.setSlice( inputImage.getCurrentSlice() );

				all.add( super.sliceSelector, allConstraints );

				if( null != super.zSelector )
					all.add( super.zSelector, allConstraints );
				if( null != super.tSelector )
					all.add( super.tSelector, allConstraints );
				if( null != super.cSelector )
					all.add( super.cSelector, allConstraints );

			}
			allConstraints.gridy--;

			GridBagLayout wingb = new GridBagLayout();
			GridBagConstraints winc = new GridBagConstraints();
			winc.anchor = GridBagConstraints.NORTHWEST;
			winc.fill = GridBagConstraints.BOTH;
			winc.weightx = 1;
			winc.weighty = 1;
			setLayout( wingb );
			add( all, winc );
			
			// Fix minimum size to the preferred size at this point
			pack();
			setMinimumSize( getPreferredSize() );
		}// end CustomWindow constructor
		
		/**
		 * Overwrite windowClosing to display the result image after closing
		 * the GUI and shut down the executor service
		 */
		@Override
		public void windowClosing( WindowEvent e )
		{							
			super.windowClosing( e );

			if( null != displayImage )
			{
				final ImagePlus result = displayImage.duplicate();
				result.setTitle( inputImage.getShortTitle() + "-edited" );
				result.setSlice( displayImage.getSlice() );
				result.show();
			}

			// remove listeners
			mergeButton.removeActionListener( listener );
			dilateButton.removeActionListener( listener );
			erodeButton.removeActionListener( listener );
			exitButton.removeActionListener( listener );

			// shut down executor service
			exec.shutdownNow();
		}
		
		/**
		 * Merge labels of current image that have been selected by either point
		 * or freehand ROIs.
		 */
		void mergeLabels()
		{
			LabelImages.mergeLabels( displayImage, displayImage.getRoi(),
					true );
			displayImage.updateAndDraw();
		}

		/**
		 * Dilate labels using a square/cube of radius 1 as structuring element
		 */
		void dilateLabels()
		{
			if( inputIs2D )
			{
				displayImage.setProcessor( Morphology.dilation(
						displayImage.getProcessor(),
						Strel.Shape.SQUARE.fromRadius( 1 ) ) );
			}
			else
			{
				displayImage.setStack( Morphology.dilation(
						displayImage.getImageStack(),
						Strel3D.Shape.CUBE.fromRadius( 1 ) ) );
			}
			displayImage.updateAndDraw();
		}

	}// end CustomWindow class
	
	

	/**
	 * Plug-in run method
	 * 
	 * @param arg plug-in arguments
	 */
	public void run(String arg) 
	{
		if ( IJ.getVersion().compareTo("1.48a") < 0 )
		{
			IJ.error( "Label Edition", "ERROR: detected ImageJ version "
					+ IJ.getVersion() + ".\nLabel Edition requires"
					+ " version 1.48a or superior, please update ImageJ!" );
			return;
		}

		// get current image
		if ( null == WindowManager.getCurrentImage() )
		{
			inputImage = IJ.openImage();
			if ( null == inputImage )
				return; // user canceled open dialog
		}
		else
			inputImage = WindowManager.getCurrentImage();

		// Check if input image is a label image
		if( LabelImages.isLabelImageType( inputImage ) == false )
		{
			IJ.error( "Label Edition", "This plugin only works on"
				+ " label images.\nPlease convert it to 8, 16 or 32-bit." );
			return;
		}
		
		// select point tool for manual label merging
		Toolbar.getInstance().setTool( Toolbar.POINT );

		inputStackCopy = inputImage.getImageStack().duplicate();
		displayImage = new ImagePlus( inputImage.getTitle(), 
				inputStackCopy );
		displayImage.setTitle( "Label Edition" );
		displayImage.setSlice( inputImage.getSlice() );

		// hide input image (to avoid accidental closing)
		inputImage.getWindow().setVisible( false );
		
		// set the 2D flag
		inputIs2D = inputImage.getImageStackSize() == 1;

		// correct Fiji error when the slices are read as frames
		if ( inputIs2D == false && 
				displayImage.isHyperStack() == false && 
				displayImage.getNSlices() == 1 )
		{
			// correct stack by setting number of frames as slices
			displayImage.setDimensions( 1, displayImage.getNFrames(), 1 );
		}

		// Build GUI
		SwingUtilities.invokeLater(
				new Runnable() {
					public void run() {
						win = new CustomWindow( displayImage );
						win.pack();
					}
				});
	}	
}// end LabelEdition class
