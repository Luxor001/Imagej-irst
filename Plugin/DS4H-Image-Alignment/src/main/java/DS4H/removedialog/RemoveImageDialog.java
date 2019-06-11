package DS4H.removedialog;

import DS4H.ImageFile;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.List;

public class RemoveImageDialog extends JDialog {
    private JPanel contentPane;
    private JButton btn_ok;
    private JButton button2;
    private JPanel pnl_buttons;
    private JScrollPane pnl_images_s;
    private List<ImageFile> imageFiles;

    public RemoveImageDialog(List<ImageFile> imageFiles) {
        $$$setupUI$$$();
        setContentPane(contentPane);
        this.setResizable(false);
        this.setTitle("Remove image");
        this.imageFiles = imageFiles;

        JPanel pnl_images = new JPanel();
        pnl_images.setLayout(new BoxLayout(pnl_images, BoxLayout.Y_AXIS));
        pnl_images_s.setViewportView(pnl_images);
        pnl_images.getInsets().set(5, 5, 5, 5);

        for (int i = 0; i < imageFiles.size(); i++) {
            ImageFile imageFile = imageFiles.get(i);

            JPanel imageFilePanel = new JPanel();
            imageFilePanel.setLayout(new BorderLayout());

            JPanel filePanel = new JPanel();
            filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.X_AXIS));
            JRadioButton rdb_select = new JRadioButton();
            rdb_select.setMargin(new Insets(0, 0, 0, 10));
            filePanel.add(rdb_select);
            JPanel pnl_fileDescription = new JPanel();
            pnl_fileDescription.setLayout(new BoxLayout(pnl_fileDescription, BoxLayout.Y_AXIS));
            pnl_fileDescription.add(new JLabel(MessageFormat.format("File {0}, {1} Images", i + 1, imageFile.getNImages(), imageFile.getNImages())));
            JLabel lbl_filePath = new JLabel(MessageFormat.format("{0}", imageFile.getPathFile()));
            lbl_filePath.setFont(new Font(lbl_filePath.getFont().getName(), lbl_filePath.getFont().getStyle(), lbl_filePath.getFont().getSize() - 1));
            lbl_filePath.setForeground(Color.GRAY);
            pnl_fileDescription.add(lbl_filePath);
            filePanel.add(pnl_fileDescription);

            JPanel imagesPanel = new JPanel();
            imagesPanel.setLayout(new BoxLayout(filePanel, BoxLayout.X_AXIS));
            imageFile.getThumbs();

            imageFilePanel.add(filePanel);
            pnl_images.add(imageFilePanel);
        }

        this.setMinimumSize(new Dimension(700, 600));
        this.setMaximumSize(new Dimension(700, 600));

        this.pack();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout(0, 0));
        pnl_buttons = new JPanel();
        pnl_buttons.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        contentPane.add(pnl_buttons, BorderLayout.SOUTH);
        button2 = new JButton();
        button2.setText("Button");
        pnl_buttons.add(button2);
        btn_ok = new JButton();
        btn_ok.setText("Button");
        pnl_buttons.add(btn_ok);
        pnl_images_s = new JScrollPane();
        contentPane.add(pnl_images_s, BorderLayout.CENTER);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
