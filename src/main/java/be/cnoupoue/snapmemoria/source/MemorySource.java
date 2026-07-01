package be.cnoupoue.snapmemoria.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "memory_sources")
public class MemorySource {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "root_path", nullable = false, unique = true)
    private String rootPath;

    @Column(name = "last_scan_at")
    private String lastScanAt;

    @Column(name = "last_scan_status")
    private String lastScanStatus;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    protected MemorySource() {
        // Required by JPA.
    }

    public MemorySource(
            String id,
            String name,
            String rootPath,
            String lastScanAt,
            String lastScanStatus,
            String createdAt,
            String updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.rootPath = rootPath;
        this.lastScanAt = lastScanAt;
        this.lastScanStatus = lastScanStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

}