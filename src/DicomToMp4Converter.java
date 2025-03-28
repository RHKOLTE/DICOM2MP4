import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReader;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.ICodec;

public class DicomToMp4Converter {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java DicomToMp4Converter <DICOM_FOLDER_PATH>");
            return;
        }

        File folder = new File(args[0]);
        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Error: Folder not found or is not a directory!");
            return;
        }

        File[] dicomFiles = folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".dcm");
            }
        });

        if (dicomFiles == null || dicomFiles.length == 0) {
            System.err.println("No DICOM files found in the folder.");
            return;
        }

        for (int i = 0; i < dicomFiles.length; i++) {
            processDicomFile(dicomFiles[i]);
        }

        System.out.println("Processing completed!");
    }

    private static void processDicomFile(File dicomFile) {
        String outputFolder = dicomFile.getParent() + File.separator + dicomFile.getName().replace(".dcm", "_frames");
        new File(outputFolder).mkdirs();

        List<File> imageFiles = new ArrayList<>();
        try {
            imageFiles = convertDicomToJpg(dicomFile, outputFolder);
            if (!imageFiles.isEmpty()) {
                String videoOutput = dicomFile.getParent() + File.separator + dicomFile.getName().replace(".dcm", ".mp4");
                createMp4FromImages(imageFiles, videoOutput);
            }
            System.out.println("Converted: " + dicomFile.getName() + " -> " + outputFolder);
        } catch (IOException e) {
            System.err.println("Error processing " + dicomFile.getName() + ": " + e.getMessage());
        }
    }

    public static List<File> convertDicomToJpg(File dicomFile, String outputFolder) throws IOException {
        Attributes metadata = readDicomMetadata(dicomFile);
        List<File> imageFiles = new ArrayList<>();

        ImageInputStream iis = ImageIO.createImageInputStream(dicomFile);
        DicomImageReader reader = new DicomImageReader(new DicomImageReaderSpi());
        reader.setInput(iis);

        int numFrames = reader.getNumImages(true);
        System.out.println("Processing " + dicomFile.getName() + " - Frames: " + numFrames);

        for (int i = 0; i < numFrames; i++) {
            BufferedImage dicomImage = reader.read(i);

            BufferedImage outputImage = new BufferedImage(dicomImage.getWidth(), dicomImage.getHeight() + 50, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = outputImage.createGraphics();
            g.drawImage(dicomImage, 0, 0, null);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 12));

            int x = 10;
            int y = dicomImage.getHeight() + 15;

            String[] metadataTags = {"InstitutionName", "Manufacturer", "PatientName", "StudyDate"};
            for (int j = 0; j < metadataTags.length; j++) {
                String tag = metadataTags[j];
                String value = metadata.getString(TagUtils.forName(tag));
                if (value != null) {
                    g.drawString(tag + ": " + value, x, y);
                    y += 15;
                }
            }

            g.dispose();

            String outputPath = outputFolder + File.separator + "frame_" + (i + 1) + ".jpg";
            File outputFile = new File(outputPath);
            ImageIO.write(outputImage, "jpg", outputFile);
            imageFiles.add(outputFile);
            System.out.println("Saved: " + outputPath);
        }

        iis.close();
        return imageFiles;
    }

    private static Attributes readDicomMetadata(File dicomFile) throws IOException {
        DicomInputStream dis = new DicomInputStream(dicomFile);
        Attributes metadata = dis.readDataset(-1, -1);
        dis.close();
        return metadata;
    }

    private static void createMp4FromImages(List<File> images, String outputMp4) {
        if (images.isEmpty()) {
            System.err.println("No images to create video.");
            return;
        }

        File firstImage = images.get(0);
        BufferedImage sampleImage;
        try {
            sampleImage = ImageIO.read(firstImage);
        } catch (IOException e) {
            System.err.println("Error reading first image: " + e.getMessage());
            return;
        }

        int width = sampleImage.getWidth();
        int height = sampleImage.getHeight();
        IMediaWriter writer = ToolFactory.makeWriter(outputMp4);
        writer.addVideoStream(0, 0, ICodec.ID.CODEC_ID_H264, width, height);

        long frameTime = 0;
        int frameRate = 25; // 25 FPS (40 ms per frame)

        for (int i = 0; i < images.size(); i++) {
            BufferedImage frameImage;
            try {
                frameImage = ImageIO.read(images.get(i));
            } catch (IOException e) {
                System.err.println("Skipping frame due to error: " + e.getMessage());
                continue;
            }
            writer.encodeVideo(0, frameImage, frameTime, TimeUnit.MILLISECONDS);
            frameTime += 1000 / frameRate; // 25 FPS = 40ms per frame
        }

        writer.close();
        System.out.println("MP4 created: " + outputMp4);
    }
}
