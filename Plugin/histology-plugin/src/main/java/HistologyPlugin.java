import histology.AboutDialog;
import histology.BufferedImagesManager;
import histology.LeastSquareImageTransformation;
import histology.LoadingDialog;
import histology.maindialog.MainDialog;
import histology.previewdialog.OnPreviewDialogEventListener;
import histology.previewdialog.PreviewDialog;
import histology.maindialog.OnMainDialogEventListener;
import histology.maindialog.event.*;
import histology.previewdialog.event.CloseDialogEvent;
import histology.previewdialog.event.IPreviewDialogEvent;
import ij.*;
import ij.gui.*;

import ij.io.OpenDialog;
import ij.plugin.ImagesToStack;
import net.imagej.ops.Op;
import net.imagej.ops.OpEnvironment;
import org.scijava.AbstractContextual;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import net.imagej.ImageJ;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Loads and displays a dataset using the ImageJ API. */
@Plugin(type = Command.class, headless = true,
		menuPath = "Plugins>HistologyPlugin")
public class HistologyPlugin extends AbstractContextual implements Op, OnMainDialogEventListener, OnPreviewDialogEventListener {
	private BufferedImagesManager manager;
	private BufferedImagesManager.BufferedImage image = null;
	private PreviewDialog previewDialog;
	private LoadingDialog loadingDialog;
	private AboutDialog aboutDialog;
	private MainDialog mainDialog;

	static private String IMAGES_CROPPED_MESSAGE = "Image size too large: image has been cropped for compatibility.";
	static private String SINGLE_IMAGE_MESSAGE = "Only one image detected in the stack: merging operation will be unavailable.";
	static private String IMAGES_OVERSIZE_MESSAGE = "Cannot open the selected image: image exceed supported dimensions.";
	static private String INSUFFICIENT_MEMORY_MESSAGE = "Insufficient computer memory (RAM) available. \n\n\t Try to increase the allocated memory by going to \n\n\t                Edit  ▶ Options  ▶ Memory & Threads \n\n\t Change \\\"Maximum Memory\\\" to, at most, 1000 MB less than your computer's total RAM).\\n\", \"Error: insufficient memory\"";
	public static void main(final String... args) {
		ImageJ ij = new ImageJ();
		ij.ui().showUI();
		HistologyPlugin plugin = new HistologyPlugin();
		plugin.setContext(ij.getContext());
		plugin.run();
	}

	@Override
	public void run() {
		// Chiediamo come prima cosa il file all'utente
		String pathFile = promptForFile();
		if (pathFile.equals("nullnull"))
			return;
		this.initialize(pathFile);
	}

	@Override
	public OpEnvironment ops() {
		return null;
	}

	@Override
	public void setEnvironment(OpEnvironment ops) {

	}

	@Override
	public void onMainDialogEvent(IMainDialogEvent dialogEvent) {
		WindowManager.setCurrentWindow(image.getWindow());
		if(dialogEvent instanceof PreviewImageEvent) {
			new Thread(() -> {
				PreviewImageEvent event = (PreviewImageEvent)dialogEvent;
				if(!event.getValue()) {
					previewDialog.close();
					return;
				}

				try {
					this.loadingDialog.showDialog();
					previewDialog = new PreviewDialog( manager.get(manager.getCurrentIndex()), this, manager.getCurrentIndex(), manager.getNImages());
				} catch (Exception e) { }
				this.loadingDialog.hideDialog();
			}).start();
		}

		if(dialogEvent instanceof ChangeImageEvent) {
			new Thread(() -> {
				ChangeImageEvent event = (ChangeImageEvent)dialogEvent;
				// per evitare memory leaks, invochiamo manualmente il garbage collector ad ogni cambio di immagine
				image = event.getChangeDirection() == ChangeImageEvent.ChangeDirection.NEXT ? this.manager.next() : this.manager.previous();

				mainDialog.changeImage(image);
				IJ.freeMemory();
				mainDialog.setPrevImageButtonEnabled(manager.hasPrevious());
				mainDialog.setNextImageButtonEnabled(manager.hasNext());
				mainDialog.setTitle(MessageFormat.format("Editor Image {0}/{1}", manager.getCurrentIndex() + 1, manager.getNImages()));
				this.loadingDialog.hideDialog();
			}).start();
			this.loadingDialog.showDialog();
		}

		if(dialogEvent instanceof DeleteRoiEvent){
		    DeleteRoiEvent event = (DeleteRoiEvent)dialogEvent;
		    image.getManager().select(event.getRoiIndex());
		    image.getManager().runCommand("Delete");
		    mainDialog.updateRoiList(image.getManager());
			if(previewDialog != null && previewDialog.isVisible())
				previewDialog.updateRoisOnScreen();

			// Get the number of rois added in each image. If they are all the same (and at least one is added), we can enable the "merge" functionality
			List<Integer> roisNumber = manager.getRoiManagers().stream().map(roiManager -> roiManager.getRoisAsArray().length).collect(Collectors.toList());
			mainDialog.setMergeButtonEnabled(roisNumber.get(0) != 0 && roisNumber.stream().distinct().count() == 1);
        }

		if(dialogEvent instanceof AddRoiEvent) {
			AddRoiEvent event = (AddRoiEvent)dialogEvent;

			int roiWidth = Toolkit.getDefaultToolkit().getScreenSize().width > image.getWidth() ? image.getWidth() : Toolkit.getDefaultToolkit().getScreenSize().width;
			roiWidth /= 12;
			OvalRoi roi = new OvalRoi (event.getClickCoords().x - (roiWidth / 2), event.getClickCoords().y - (roiWidth/2), roiWidth, roiWidth);
			roi.setCornerDiameter(10);
			roi.setFillColor(Color.red);
			roi.setStrokeColor(Color.blue);

			// get roughly the 0,25% of the width of the image as stroke width of th rois added.
			// If the resultant value is too small, set it as the minimum value
			int strokeWidth = (int) (image.getWidth() * 0.0025) > 3 ? (int) (image.getWidth() * 0.0025) : 3;
			roi.setStrokeWidth(strokeWidth);

			roi.setImage(image);
			image.getManager().add(image, roi, 0);

			mainDialog.updateRoiList(image.getManager());

			image.getManager().runCommand("show all with labels");
			if(previewDialog != null && previewDialog.isVisible())
				previewDialog.updateRoisOnScreen();

			// Get the number of rois added in each image. If they are all the same (and at least one is added), we can enable the "merge" functionality
			List<Integer> roisNumber = manager.getRoiManagers().stream().map(roiManager -> roiManager.getRoisAsArray().length).collect(Collectors.toList());
			mainDialog.setMergeButtonEnabled(roisNumber.get(0) != 0 && roisNumber.stream().distinct().count() == 1);
		}

		if(dialogEvent instanceof SelectedRoiEvent) {
			SelectedRoiEvent event = (SelectedRoiEvent)dialogEvent;
			Arrays.stream(image.getManager().getSelectedRoisAsArray()).forEach(roi -> roi.setStrokeColor(Color.BLUE));
			image.getManager().select(event.getRoiIndex());

			image.getRoi().setStrokeColor(Color.yellow);
			image.updateAndDraw();
			if(previewDialog != null && previewDialog.isVisible())
				previewDialog.updateRoisOnScreen();
		}

		if(dialogEvent instanceof DeselectedRoiEvent) {
			DeselectedRoiEvent event = (DeselectedRoiEvent)dialogEvent;
			Arrays.stream(image.getManager().getSelectedRoisAsArray()).forEach(roi -> roi.setStrokeColor(Color.BLUE));
			image.getManager().select(event.getRoiIndex());
		}

		if(dialogEvent instanceof MergeEvent) {
			ArrayList<ImagePlus> images = new ArrayList<>();
			images.add(manager.get(0));
			for(int i=1; i < manager.getNImages(); i++)
				images.add(LeastSquareImageTransformation.transform(manager.get(i),manager.get(0)));
			ImagePlus stack = ImagesToStack.run(images.toArray(new ImagePlus[images.size()]));
			stack.show("Merged images");
		}

		if(dialogEvent instanceof OpenFileEvent || dialogEvent instanceof ExitEvent) {
			boolean roisPresent = manager.getRoiManagers().stream().filter(manager -> manager.getRoisAsArray().length != 0).count() > 0;
			if(roisPresent){
				String[] buttons = { "Yes", "No"};
				String message = dialogEvent instanceof OpenFileEvent ? "This will replace the existing image. Proceed anyway?" : "You will lose the existing added landmarks. Proceed anyway?";
				int answer = JOptionPane.showOptionDialog(null, message, "Careful now",
						JOptionPane.WARNING_MESSAGE, 0, null, buttons, buttons[1]);

				if(answer == 1)
					return;
			}

			if(dialogEvent instanceof OpenFileEvent){
				String pathFile = promptForFile();
				if (pathFile.equals("nullnull"))
					return;
				this.disposeAll();
				this.initialize(pathFile);
			}
			else {
				disposeAll();
				System.exit(0);
			}
		}

		if(dialogEvent instanceof OpenAboutEvent) {
			this.aboutDialog.setVisible(true);
		}
	}

	@Override
	public void onPreviewDialogEvent(IPreviewDialogEvent dialogEvent) {
		if(dialogEvent instanceof histology.previewdialog.event.ChangeImageEvent) {
			new Thread(() -> {
				WindowManager.setCurrentWindow(image.getWindow());
				histology.previewdialog.event.ChangeImageEvent event = (histology.previewdialog.event.ChangeImageEvent)dialogEvent;
				BufferedImagesManager.BufferedImage image = manager.get(event.getIndex());
				previewDialog.changeImage(image);
				IJ.freeMemory();
				this.loadingDialog.hideDialog();
			}).start();
			this.loadingDialog.showDialog();
		}

		if(dialogEvent instanceof CloseDialogEvent) {
			mainDialog.setPreviewWindowCheckBox(false);
		}
	}

	/**
	 * Initialize the plugin opening the file specified in the mandatory param
	 */
	public void initialize(String pathFile) {

		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			if(e instanceof  OutOfMemoryError){
				JOptionPane.showMessageDialog(null, INSUFFICIENT_MEMORY_MESSAGE, "Error: insufficient memory", JOptionPane.ERROR_MESSAGE);
				System.exit(0);
			}
		});
		this.aboutDialog = new AboutDialog();
		this.loadingDialog = new LoadingDialog();
		this.loadingDialog.showDialog();

		try {
			manager = new BufferedImagesManager(pathFile);
			image = manager.next();
			mainDialog = new MainDialog(image, this);
			mainDialog.setPrevImageButtonEnabled(manager.hasPrevious());
			mainDialog.setNextImageButtonEnabled(manager.hasNext());
			mainDialog.setTitle(MessageFormat.format("Editor Image {0}/{1}", manager.getCurrentIndex() + 1, manager.getNImages()));

			mainDialog.pack();
			mainDialog.setVisible(true);

			this.loadingDialog.hideDialog();
			if(manager.isReducedImageMode())
				JOptionPane.showMessageDialog(null, IMAGES_CROPPED_MESSAGE, "Info", JOptionPane.INFORMATION_MESSAGE);
			if(manager.getNImages() == 1)
				JOptionPane.showMessageDialog(null, SINGLE_IMAGE_MESSAGE, "Warning", JOptionPane.WARNING_MESSAGE);
		}
		catch (BufferedImagesManager.ImageOversizeException e) {
			JOptionPane.showMessageDialog(null, IMAGES_OVERSIZE_MESSAGE);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String promptForFile() {
		OpenDialog od = new OpenDialog("Selezionare un'immagine");
		String dir = od.getDirectory();
		String name = od.getFileName();
		return (dir + name);
	}

	/**
	 * Dispose all the opened workload objects.
	 */
	private void disposeAll() {
		try {
			this.mainDialog.dispose();
			this.loadingDialog.hideDialog();
			this.loadingDialog.dispose();
			if(this.previewDialog != null)
				this.previewDialog.dispose();
			this.manager.dispose();
		} catch (IOException e) {
			e.printStackTrace();
		}
		IJ.freeMemory();
	}
}

