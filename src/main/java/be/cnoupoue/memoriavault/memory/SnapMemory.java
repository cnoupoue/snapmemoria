package be.cnoupoue.memoriavault.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "memories")
public class SnapMemory {

  @Id private String id;

  @Column(name = "source_id", nullable = false)
  private String sourceId;

  @Column(name = "external_memory_id", nullable = false)
  private String externalMemoryId;

  @Column(name = "captured_at", nullable = false)
  private String capturedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "media_type", nullable = false)
  private SnapMemoryType mediaType;

  @Column(name = "main_path", nullable = false)
  private String mainPath;

  @Column(name = "overlay_path")
  private String overlayPath;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "last_modified_at", nullable = false)
  private String lastModifiedAt;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "updated_at", nullable = false)
  private String updatedAt;

  @Column(name = "is_favorite", nullable = false)
  private boolean isFavorite;

  @Column(name = "favorited_at")
  private String favoritedAt;

  protected SnapMemory() {
    // Required by JPA.
  }

  public SnapMemory(
      String id,
      String sourceId,
      String externalMemoryId,
      String capturedAt,
      SnapMemoryType mediaType,
      String mainPath,
      String overlayPath,
      long fileSizeBytes,
      String lastModifiedAt,
      String createdAt,
      String updatedAt) {
    this(
        id,
        sourceId,
        externalMemoryId,
        capturedAt,
        mediaType,
        mainPath,
        overlayPath,
        fileSizeBytes,
        lastModifiedAt,
        createdAt,
        updatedAt,
        false,
        null);
  }

  public SnapMemory(
      String id,
      String sourceId,
      String externalMemoryId,
      String capturedAt,
      SnapMemoryType mediaType,
      String mainPath,
      String overlayPath,
      long fileSizeBytes,
      String lastModifiedAt,
      String createdAt,
      String updatedAt,
      boolean isFavorite,
      String favoritedAt) {
    this.id = id;
    this.sourceId = sourceId;
    this.externalMemoryId = externalMemoryId;
    this.capturedAt = capturedAt;
    this.mediaType = mediaType;
    this.mainPath = mainPath;
    this.overlayPath = overlayPath;
    this.fileSizeBytes = fileSizeBytes;
    this.lastModifiedAt = lastModifiedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.isFavorite = isFavorite;
    this.favoritedAt = favoritedAt;
  }

  public void markFavorite(String favoritedAt) {
    if (!isFavorite) {
      this.isFavorite = true;
      this.favoritedAt = favoritedAt;
      this.updatedAt = favoritedAt;
    }
  }

  public void removeFavorite(String updatedAt) {
    if (isFavorite || favoritedAt != null) {
      this.isFavorite = false;
      this.favoritedAt = null;
      this.updatedAt = updatedAt;
    }
  }

  public void updateIndexedMetadata(SnapMemory scannedMemory) {
    this.externalMemoryId = scannedMemory.externalMemoryId;
    this.capturedAt = scannedMemory.capturedAt;
    this.mediaType = scannedMemory.mediaType;
    this.mainPath = scannedMemory.mainPath;
    this.overlayPath = scannedMemory.overlayPath;
    this.fileSizeBytes = scannedMemory.fileSizeBytes;
    this.lastModifiedAt = scannedMemory.lastModifiedAt;
    this.updatedAt = scannedMemory.updatedAt;
  }
}
