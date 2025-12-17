package dev.engineeringlab.stratify.structure.backup.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Represents a backup transaction that groups multiple backup operations.
 *
 * <p>Transactions allow atomic backup/restore of multiple files. If any operation fails, the entire
 * transaction can be rolled back.
 */
public interface BackupTransaction extends AutoCloseable {

  /**
   * Returns the transaction ID.
   *
   * @return unique transaction identifier
   */
  String getId();

  /**
   * Adds a file to be backed up in this transaction.
   *
   * @param path the file to backup
   * @return the backup result
   */
  BackupResult backup(Path path);

  /**
   * Returns the list of files backed up in this transaction.
   *
   * @return list of backup results
   */
  List<BackupResult> getBackups();

  /** Commits the transaction, finalizing all backups. */
  void commit();

  /** Rolls back the transaction, restoring all original files. */
  void rollback();

  /**
   * Returns whether the transaction is still active.
   *
   * @return true if transaction is active
   */
  boolean isActive();

  /** Closes the transaction. If not committed, performs rollback. */
  @Override
  void close();
}
