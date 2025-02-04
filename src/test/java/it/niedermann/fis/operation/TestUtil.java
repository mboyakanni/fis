package it.niedermann.fis.operation;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.core.io.ClassPathResource;

import it.niedermann.fis.main.model.OperationDto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestUtil {

    public static FTPFile createFTPFile(String name, Instant timestamp) {
        return createFTPFile(name, timestamp, 0);
    }

    public static FTPFile createFTPFile(String name, Instant timestamp, long size) {
        return createFTPObject(name, timestamp, FTPFile.FILE_TYPE, size);
    }

    public static FTPFile createFTPObject(String name, Instant timestamp, int type) {
        return createFTPObject(name, timestamp, type, 0);
    }

    public static FTPFile createFTPObject(String name, Instant timestamp, int type, long size) {
        final var ftpFile = new FTPFile();
        ftpFile.setName(name);
        ftpFile.setTimestamp(GregorianCalendar.from(timestamp.atZone(ZoneId.systemDefault())));
        ftpFile.setType(type);
        ftpFile.setSize(size);
        return ftpFile;
    }

    public static Map<Integer, Sample<String, OperationDto>> getOperationSamples(String dir) throws IOException {
        final var map = Arrays.stream(new ClassPathResource(Path.of("samples", dir).toString()).getFile().listFiles())
                .map(File::getName)
                .filter(fileName -> fileName.endsWith("-sample.txt"))
                .map(fileName -> fileName.substring(0, fileName.indexOf("-")))
                .map(Integer::parseInt)
                .collect(Collectors.<Integer, Integer, Sample<String, OperationDto>>toMap(
                        number -> number,
                        number -> new Sample<String, OperationDto>(
                                getSampleInput(dir, number),
                                getSampleExpected(dir, number))));
        assertTrue(map.size() > 0, "Expected to find at least one sample");
        return map;
    }

    private static String getSampleInput(String dir, int number) {
        try {
            return IOUtils.toString(
                    new ClassPathResource(Path.of("samples", dir, number + "-sample.txt").toString()).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static OperationDto getSampleExpected(String dir, int number) {
        try {
            return new ObjectMapper().readValue(
                    new ClassPathResource(Path.of("samples", dir, number + "-expected.json").toString()).getInputStream(),
                    OperationDto.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static record Sample<T, U> (T input, U expected) {
    }
}
