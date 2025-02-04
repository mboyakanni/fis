package it.niedermann.fis.operation.remote.ftp;

import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static it.niedermann.fis.operation.TestUtil.createFTPFile;
import static it.niedermann.fis.operation.TestUtil.createFTPObject;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OperationFTPRepositoryTest {

    private OperationFTPRepository repository;
    private OperationFTPClient ftpClient;

    @BeforeEach
    public void setup() throws IOException {
        final var config = mock(FtpConfiguration.class);
        when(config.fileSuffix()).thenReturn(".pdf");
        when(config.checkUploadCompleteInterval()).thenReturn(0L);
        when(config.checkUploadCompleteMaxAttempts()).thenReturn(10);
        when(config.maxFileSize()).thenReturn(10_000_000L);
        ftpClient = mock(OperationFTPClient.class);
        when(ftpClient.login(any(), any())).thenReturn(true);
        this.repository = new OperationFTPRepository(
                config,
                ftpClient
        );
    }

    @Test
    public void shouldReturnEmptyIfAnIORelatedErrorOccurs() throws IOException {
        when(ftpClient.listFiles(any())).thenThrow(new IOException());
        assertTrue(repository.poll().isEmpty());
    }

    @Test
    public void shouldReturnEmptyIfNoFileIsPresent() throws IOException {
        doFirstPoll();

        List.of(
                FTPFile.DIRECTORY_TYPE,
                FTPFile.UNKNOWN_TYPE,
                FTPFile.SYMBOLIC_LINK_TYPE
        ).stream()
                .map(type -> createFTPObject("Foo", now(), FTPFile.DIRECTORY_TYPE))
                .map(ftpObject -> new FTPFile[]{ftpObject})
                .forEach(fileList -> {
                    try {
                        when(ftpClient.listFiles(any())).thenReturn(fileList);
                        assertTrue(repository.poll().isEmpty());
                    } catch (IOException e) {
                        fail(e);
                    }
                });
    }

    @Test
    public void shouldFilterFolders() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[0]);
        assertTrue(repository.poll().isEmpty());
    }

    @Test
    public void shouldNotReturnTheSameFileMultipleTimesAfterFirstPoll() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Foo.pdf", now())
        });

        assertTrue(repository.poll().isPresent());
        assertTrue(repository.poll().isEmpty());
    }

    @Test
    public void shouldNotReturnExistingFilesOnTheFirstPoll() throws IOException {
        final var file1 = createFTPFile("Foo.pdf", now());
        final var file2 = createFTPFile("Bar.pdf", now());
        final var file3 = createFTPFile("Qux.pdf", now());

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                file1, file2
        });
        assertTrue(repository.poll().isEmpty());
        assertTrue(repository.poll().isEmpty());
        assertTrue(repository.poll().isEmpty());

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                file1, file3, file2
        });

        final var result = repository.poll();
        assertTrue(result.isPresent());
        assertEquals("Qux.pdf", result.get().getName());
    }

    @Test
    public void shouldNotReturnAlreadyExistingFilesWhenPollingMultipleTimes() throws IOException {
        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Foo.pdf", now())
        });

        assertTrue(repository.poll().isEmpty());
        assertTrue(repository.poll().isEmpty());
    }

    @Test
    public void shouldReturnOnlyFilesWhichHaveBeenAddedAfterPollingStart() throws IOException {
        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Foo.pdf", now().minus(3, ChronoUnit.MINUTES))
        });
        final var existingFtpFile = repository.poll();
        assertTrue(existingFtpFile.isEmpty());

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Bar.pdf", now())
        });
        final var addedFtpFile = repository.poll();
        assertTrue(addedFtpFile.isPresent());
        assertEquals("Bar.pdf", addedFtpFile.get().getName());
    }

    @Test
    public void shouldFilterBySuffix() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Foo.doc", now().minus(3, ChronoUnit.MINUTES))
        });
        assertTrue(repository.poll().isEmpty());
    }

    @Test
    public void shouldReturnTheMostCurrentFile() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Foo.pdf", now()),
                createFTPFile("Bar.pdf", now().minus(3, ChronoUnit.MINUTES))
        });
        final var ftpFile1 = repository.poll();
        assertTrue(ftpFile1.isPresent());
        assertEquals("Foo.pdf", ftpFile1.get().getName());

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("Foo.pdf", now().minus(3, ChronoUnit.MINUTES)),
                createFTPFile("Bar.pdf", now())
        });
        final var ftpFile2 = repository.poll();
        assertTrue(ftpFile2.isPresent());
        assertEquals("Bar.pdf", ftpFile2.get().getName());
    }

    @Test
    public void shouldTakeTheAlphabeticallyHigherFileInCaseOfEqualTimestampsRespectingPastMatches() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(
                new FTPFile[]{
                        createFTPFile("thl-1.pdf", Instant.ofEpochMilli(1646654160000L)),
                },
                new FTPFile[]{
                        createFTPFile("thl-1.pdf", Instant.ofEpochMilli(1646654160000L)),
                        createFTPFile("thl-2.pdf", Instant.ofEpochMilli(1646654220000L))
                },
                new FTPFile[]{
                        createFTPFile("brand-3.pdf", Instant.ofEpochMilli(1646654220000L)),
                        createFTPFile("thl-1.pdf", Instant.ofEpochMilli(1646654160000L)),
                        createFTPFile("thl-2.pdf", Instant.ofEpochMilli(1646654220000L))
                },
                new FTPFile[]{
                        createFTPFile("brand-3.pdf", Instant.ofEpochMilli(1646654220000L)),
                        createFTPFile("thl-1.pdf", Instant.ofEpochMilli(1646654160000L)),
                        createFTPFile("thl-2.pdf", Instant.ofEpochMilli(1646654220000L)),
                        createFTPFile("thl-4.pdf", Instant.ofEpochMilli(1646654220000L))
                }
        );

        final var thl1 = repository.poll();
        assertTrue(thl1.isPresent());
        assertEquals("thl-1.pdf", thl1.get().getName());

        final var thl2 = repository.poll();
        assertTrue(thl2.isPresent());
        assertEquals("thl-2.pdf", thl2.get().getName());

        final var brand3 = repository.poll();
        assertTrue(brand3.isPresent());
        assertEquals("brand-3.pdf", brand3.get().getName());

        final var thl4 = repository.poll();
        assertTrue(thl4.isPresent());
        assertEquals("thl-4.pdf", thl4.get().getName());
    }

    @Test
    public void shouldTakeTheAlphabeticallyHigherFileInCaseOfEqualTimestamps() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[]{
                createFTPFile("brand.pdf", Instant.ofEpochMilli(1646641500000L)),
                createFTPFile("thl.pdf", Instant.ofEpochMilli(1646641500000L))
        });
        final var ftpFile = repository.poll();
        assertTrue(ftpFile.isPresent());
        assertEquals("thl.pdf", ftpFile.get().getName());
    }

    @Test
    public void pollingShouldIgnoreHugeFiles() throws IOException {
        doFirstPoll();

        when(ftpClient.listFiles(any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 10_000_001L)}
        );
        assertTrue(repository.poll().isEmpty());
    }

    @Test
    public void downloadShouldReturnEmptyWhenRetrievingFileThrowsIOException() throws IOException {
        when(ftpClient.retrieveFile(any(), any())).thenThrow(IOException.class);
        assertTrue(repository.download(new FTPFile()).isEmpty());
    }

    @Test
    public void downloadShouldReturnEmptyWhenRetrievingFileFails() throws IOException {
        when(ftpClient.retrieveFile(any(), any())).thenReturn(false);
        assertTrue(repository.download(new FTPFile()).isEmpty());
    }

    @Test
    public void downloadShouldReturnLocalFile() throws IOException {
        when(ftpClient.retrieveFile(any(), any())).thenReturn(true);
        assertTrue(repository.download(new FTPFile()).isPresent());
    }

    @Test
    public void uploadAlreadyCompleted() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 444)}
        );
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(444);
        assertTrue(repository.awaitUploadCompletion(file).isPresent());
    }

    @Test
    public void uploadAlreadyCompletedTotalSizeZero() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 0)}
        );
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(0);
        assertTrue(repository.awaitUploadCompletion(file).isPresent());
    }

    @Test
    public void uploadInProgress() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 333)},
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 444)},
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 555)}
        );
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(111);
        assertTrue(repository.awaitUploadCompletion(file).isPresent());
    }

    @Test
    public void uploadInProgressStartingWithZero() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 333)},
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 444)},
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 555)}
        );
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(0);
        assertTrue(repository.awaitUploadCompletion(file).isPresent());
    }

    @Test
    public void awaitUploadCompletionShouldStopAfterConfiguredAttempts() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 2)},
                IntStream
                        .rangeClosed(3, 11) // Bigger than the configured count of attempts
                        .boxed()
                        .map(size -> new FTPFile[]{createFTPFile("Foo.pdf", now(), size)})
                        .toArray(FTPFile[][]::new)
        );
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(1);
        assertTrue(repository.awaitUploadCompletion(file).isEmpty());
    }

    @Test
    public void awaitUploadCompletionShouldGracefullyHandleErrors() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenThrow(new IOException());
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(111);
        assertTrue(repository.awaitUploadCompletion(file).isEmpty());
    }

    @Test
    public void awaitUploadCompletionShouldReturnEmptyForHugeFiles() throws IOException {
        when(ftpClient.listFiles(any(), any())).thenReturn(
                new FTPFile[]{createFTPFile("Foo.pdf", now(), 10_000_001L)}
        );
        final var file = new FTPFile();
        file.setName("Foo.pdf");
        file.setSize(0);
        assertTrue(repository.awaitUploadCompletion(file).isEmpty());
    }

    private void doFirstPoll() throws IOException {
        when(ftpClient.listFiles(any())).thenReturn(new FTPFile[0]);
        repository.poll();
    }
}
