CREATE TABLE memory_scan_jobs (
                                  id TEXT PRIMARY KEY,

                                  source_id TEXT NOT NULL,
                                  status TEXT NOT NULL,

                                  total_files BIGINT NOT NULL DEFAULT 0,
                                  files_processed BIGINT NOT NULL DEFAULT 0,

                                  main_images BIGINT NOT NULL DEFAULT 0,
                                  main_videos BIGINT NOT NULL DEFAULT 0,
                                  overlays BIGINT NOT NULL DEFAULT 0,

                                  indexed_memories BIGINT NOT NULL DEFAULT 0,
                                  attached_overlays BIGINT NOT NULL DEFAULT 0,
                                  unmatched_overlays BIGINT NOT NULL DEFAULT 0,

                                  unsupported_files BIGINT NOT NULL DEFAULT 0,
                                  unreadable_files BIGINT NOT NULL DEFAULT 0,

                                  error_message TEXT,

                                  started_at TEXT NOT NULL,
                                  completed_at TEXT,
                                  updated_at TEXT NOT NULL,

                                  CONSTRAINT fk_memory_scan_jobs_source
                                      FOREIGN KEY (source_id)
                                          REFERENCES memory_sources(id)
);

CREATE INDEX idx_memory_scan_jobs_source_id
    ON memory_scan_jobs(source_id);

CREATE INDEX idx_memory_scan_jobs_status
    ON memory_scan_jobs(status);