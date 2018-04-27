package com.nginx.image.core;

import com.nginx.image.core.PhotoResizer.ImageInformation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.nginx.image.configs.PhotoResizerConfiguration;
import com.nginx.image.net.S3Client;
import com.nginx.image.util.ImageSizeEnum;
import com.nginx.image.util.ResizerException;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.ExifTagConstants;
import org.apache.sanselan.formats.tiff.constants.TagInfo;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;


/**
 * PhotoTransformer.java
 * PhotoTransformer
 *
 * Class that does the work of reorienting an image
 *
 * Copyright © 2018 NGINX Inc. All rights reserved.
 */
public class PhotoTransformer {

    // The logger for this instance of the service
    private static final Logger LOGGER = LoggerFactory.getLogger(PhotoTransformer.class);

    // The string to use when displaying the class instance
    private final String classInstance = " class instance = " + System.identityHashCode(this);


    public PhotoTransformer()
    {

    }


    /**
     * Method which takes an image file and calls {@link #transformImage}
     *
     * @param width the width to use in the transformation
     * @param height the height to use in the transformation
     * @param originalImage the image to transform
     * @param originalBuffImage the buffered image
     */
    private BufferedImage PhotoTransformer(int width, int height, File originalImage, BufferedImage originalBuffImage) throws IOException, ImageReadException
    {
        try {
            JpegImageMetadata meta=((JpegImageMetadata) Sanselan.getMetadata(originalImage));
            TiffImageMetadata data=null;
            if (meta != null) {
                data = meta.getExif();
            }
            int orientation = 0;
            if (data != null && data.findField(ExifTagConstants.EXIF_TAG_ORIENTATION) != null) {
                orientation = data.findField(ExifTagConstants.EXIF_TAG_ORIENTATION).getIntValue();
                if(orientation == 1) return originalBuffImage;
                // THis is returned here because the image doesn't need to be reoriented at all.
            }
            AffineTransform t = getExifTransformation(new ImageInformation(orientation,width,height));
            originalBuffImage = transformImage(originalBuffImage,t);
            return originalBuffImage;
        } catch (Exception e) {
            LOGGER.error("This is the general exception message: ", e);
            throw e;
        }
    }

    /**
     * Transforms an image using {@link AffineTransformOp} and {@link Graphics2D}
     *
     * @param image the image to transform
     * @param transform the {@link AffineTransform} object
     *
     * @return a {@link BufferedImage} object
     */
    private static BufferedImage transformImage(BufferedImage image, AffineTransform transform) {
        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);

        BufferedImage destinationImage = op.createCompatibleDestImage(image, (image.getType() == BufferedImage.TYPE_BYTE_GRAY) ? image.getColorModel() : null);
        Graphics2D g = destinationImage.createGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, destinationImage.getWidth(), destinationImage.getHeight());
        destinationImage = op.filter(image, destinationImage);
        return destinationImage;
    }

    /**
     * Static method that creates and returns a new {@link AffineTransform} object based on
     * the info param
     *
     * @param info an {@link ImageInformation} object
     *
     * @return an {@link AffineTransform} object
     */
    private static AffineTransform getExifTransformation(ImageInformation info) {
        AffineTransform t = new AffineTransform();

        switch (info.orientation) {
            case 1:
                break;
            case 2: // Flip X
                t.scale(-1.0, 1.0);
                t.translate(-info.width, 0);
                break;
            case 3: // PI rotation
                t.translate(info.width, info.height);
                t.rotate(Math.PI);
                break;
            case 4: // Flip Y
                t.scale(1.0, -1.0);
                t.translate(0, -info.height);
                break;
            case 5: // - PI/2 and Flip X
                t.rotate(-Math.PI / 2);
                t.scale(-1.0, 1.0);
                break;
            case 6: // -PI/2 and -width
                t.translate(info.height, 0);
                t.rotate(Math.PI / 2);
                break;
            case 7: // PI/2 and Flip
                t.scale(-1.0, 1.0);
                t.translate(-info.height, 0);
                t.translate(0, info.width);
                t.rotate(  3 * Math.PI / 2);
                break;
            case 8: // PI / 2
                t.translate(0, info.width);
                t.rotate(  3 * Math.PI / 2);
                break;
        }
        return t;
    }
}

