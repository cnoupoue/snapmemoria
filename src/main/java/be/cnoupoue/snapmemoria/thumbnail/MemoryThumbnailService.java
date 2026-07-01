package be.cnoupoue.snapmemoria.thumbnail;

import be.cnoupoue.snapmemoria.memory.SnapMemory;
import be.cnoupoue.snapmemoria.memory.SnapMemoryRepository;
import be.cnoupoue.snapmemoria.memory.SnapMemoryType;
import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MemoryThumbnailService {

    private final SnapMemoryRepository snapMemoryRepository;
    private final MemorySourceRepository memorySourceRepository;
    private final Path thumbnailDirectory;
    private final int maxWidth;
    private final int maxHeight;

    private final Map<String, Object> thumbnailLocks = new ConcurrentHashMap<>();

    public MemoryThumbnailService(
            SnapMemoryRepository snapMemoryRepository,
            MemorySourceRepository memorySourceRepository,
            @Value("${snapmemoria.thumbnail.directory}") String thumbnailDirectory,
            @Value("${snapmemoria.thumbnail.max-width}") int maxWidth,
            @Value("${snapmemoria.thumbnail.max-height}") int maxHeight
    ) {
        this.snapMemoryRepository = snapMemoryRepository;
        this.memorySourceRepository = memorySourceRepository;
        this.thumbnailDirectory = Path.of(thumbnailDirectory)
                .toAbsolutePath()
                .normalize();
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public FileSystemResource getThumbnail(String memoryId) {
        SnapMemory memory = snapMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Memory not found."
                ));

        if (memory.getMediaType() != SnapMemoryType.IMAGE) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Video thumbnails are not available yet."
            );
        }

        Path thumbnailPath = thumbnailDirectory.resolve(memory.getId() + ".jpg");

        try {
            Files.createDirectories(thumbnailDirectory);

            if (Files.isRegularFile(thumbnailPath)) {
                return new FileSystemResource(thumbnailPath);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not prepare the thumbnail cache."
            );
        }

        Object lock = thumbnailLocks.computeIfAbsent(memoryId, ignored -> new Object());

        synchronized (lock) {
            try {
                if (!Files.isRegularFile(thumbnailPath)) {
                    generateThumbnail(memory, thumbnailPath);
                }

                return new FileSystemResource(thumbnailPath);
            } finally {
                thumbnailLocks.remove(memoryId);
            }
        }
    }

    private void generateThumbnail(
            SnapMemory memory,
            Path thumbnailPath
    ) {
        Path mainImagePath = resolveSecureMediaPath(
                memory.getSourceId(),
                memory.getMainPath(),
                "The original image file is unavailable."
        );

        try {
            BufferedImage mainImage = ImageIO.read(mainImagePath.toFile());

            if (mainImage == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "The original image format is not supported."
                );
            }

            BufferedImage imageWithOverlay = applyOverlayIfPresent(
                    mainImage,
                    memory
            );

            BufferedImage thumbnail = resize(imageWithOverlay);

            ImageIO.write(
                    thumbnail,
                    "jpg",
                    thumbnailPath.toFile()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not generate the image thumbnail."
            );
        }
    }

    private BufferedImage applyOverlayIfPresent(
            BufferedImage mainImage,
            SnapMemory memory
    ) {
        if (memory.getOverlayPath() == null) {
            return mainImage;
        }

        Path overlayPath = resolveSecureMediaPath(
                memory.getSourceId(),
                memory.getOverlayPath(),
                "The Snapchat overlay file is unavailable."
        );

        try {
            BufferedImage overlayImage = ImageIO.read(overlayPath.toFile());

            if (overlayImage == null) {
                return mainImage;
            }

            BufferedImage combinedImage = new BufferedImage(
                    mainImage.getWidth(),
                    mainImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );

            Graphics2D graphics = combinedImage.createGraphics();

            try {
                graphics.drawImage(
                        mainImage,
                        0,
                        0,
                        mainImage.getWidth(),
                        mainImage.getHeight(),
                        null
                );

                graphics.drawImage(
                        overlayImage,
                        0,
                        0,
                        mainImage.getWidth(),
                        mainImage.getHeight(),
                        null
                );
            } finally {
                graphics.dispose();
            }

            return combinedImage;
        } catch (IOException exception) {
            return mainImage;
        }
    }

    private BufferedImage resize(BufferedImage sourceImage) {
        double scale = Math.min(
                (double) maxWidth / sourceImage.getWidth(),
                (double) maxHeight / sourceImage.getHeight()
        );

        scale = Math.min(scale, 1.0);

        int targetWidth = Math.max(
                1,
                (int) Math.round(sourceImage.getWidth() * scale)
        );

        int targetHeight = Math.max(
                1,
                (int) Math.round(sourceImage.getHeight() * scale)
        );

        BufferedImage resizedImage = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = resizedImage.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );

            graphics.drawImage(
                    sourceImage,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null
            );
        } finally {
            graphics.dispose();
        }

        return resizedImage;
    }

    private Path resolveSecureMediaPath(
            String sourceId,
            String storedMediaPath,
            String unavailableMessage
    ) {
        MemorySource source = memorySourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Memory source not found."
                ));

        try {
            Path sourceRootPath = Path.of(source.getRootPath())
                    .toRealPath();

            Path mediaPath = Path.of(storedMediaPath)
                    .toRealPath();

            if (!mediaPath.startsWith(sourceRootPath)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "The requested file is outside the configured memory source."
                );
            }

            if (!Files.isRegularFile(mediaPath) || !Files.isReadable(mediaPath)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        unavailableMessage
                );
            }

            return mediaPath;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    unavailableMessage
            );
        }
    }
}