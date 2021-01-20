package it.niedermann.fis.operation;

import it.niedermann.fis.operation.parser.OperationFaxParser;
import it.niedermann.fis.socket.SocketRegistry;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

@Service
public class OperationDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(OperationDispatcher.class);

    private final SocketRegistry socketRegistry;
    private final SimpMessagingTemplate template;

    private final ITesseract tesseract;
    private final FTPClient ftpClient;
    private final OperationFaxParser parser;

    private String lastPdfName = "";
    @Value("${ftp.path}")
    private String ftpPath;
    @Value("${ftp.file.suffix}")
    private String ftpFileSuffix;

    public OperationDispatcher(
            SocketRegistry socketRegistry,
            SimpMessagingTemplate template,
            @Value("${ftp.host}") String ftpUrl,
            @Value("${ftp.user}") String ftpUsername,
            @Value("${ftp.password}") String ftpPassword,
            @Value("#{new Long('${ftp.poll.interval}')}") Integer ftpPollInterval,
            @Value("${tesseract.tessdata}") String tessdataPath,
            @Value("${tesseract.lang}") String tessLang) throws IOException {
        this.socketRegistry = socketRegistry;
        this.template = template;

        if (ObjectUtils.isEmpty(tessdataPath)) {
            tessdataPath = System.getProperty("user.home") + "/tessdata";
        }
        tesseract = new Tesseract();
        tesseract.setTessVariable("LC_ALL", "C");
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(tessLang);

        ftpClient = new FTPClient();
        ftpClient.connect(ftpUrl);
        if (!ftpClient.login(ftpUsername, ftpPassword)) {
            throw new IllegalArgumentException("🚒 Could not connect to FTP server + " + ftpUrl + ". Please check FTP credentials.");
        }
        logger.info("🚒 Connected to FTP server " + ftpUrl + ", palling each " + ftpPollInterval / 1000 + " seconds.");

        parser = OperationFaxParser.create("mittelfranken-sued");
    }

    @Scheduled(fixedDelayString = "${ftp.poll.interval}")
    public void dispatch() {
        final Collection<String> listeners = socketRegistry.getListeners();
        if (listeners.size() == 0) {
            logger.debug("🚒 Skip operations poll because no listeners are registered.");
            return;
        }
        try {
            final Optional<FTPFile> ftpFileOptional = poll(lastPdfName);
            if (ftpFileOptional.isPresent()) {
                final FTPFile ftpFile = ftpFileOptional.get();
                logger.debug("🚒 Found a new file: \"" + ftpFile.getName() + "\"");

                if ("".equals(lastPdfName)) {
                    logger.debug("🚒 Skipping first recognized file after startup");
                    return;
                }

                lastPdfName = ftpFile.getName();
                logger.info("🚒 New incoming PDF detected: " + ftpFile.getName());

                final File localFile = File.createTempFile("operation-", ".pdf");
                logger.debug("🚒 → Created temporary file: " + localFile.getName());

                final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(localFile));
                final boolean success = ftpClient.retrieveFile(ftpPath + "/" + ftpFile.getName(), outputStream);
                outputStream.close();

                if (success) {
                    logger.info("🚒 → Downloaded file to: " + localFile.getName());
                    final String ocrText = tesseract.doOCR(localFile);
                    final OperationDto dto = parser.parse(ocrText);
                    notifyListeners(listeners, template, dto);
                    logger.info("🚒 → Successfully extracted text from PDF file.");
                    if (!localFile.delete()) {
                        logger.warn("🚒 → Could not delete downloaded FTP file!");
                    }
                } else {
                    logger.warn("🚒 → Could not download new FTP file!");
                }
            } else {
                logger.debug("🚒 → No new file with suffix \"" + ftpFileSuffix + "\" is present at the server.");
            }
        } catch (TesseractException e) {
            logger.error("🚒 → Could not parse", e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private Optional<FTPFile> poll(String lastPdfName) throws IOException {
        logger.debug("🚒 Checking FTP server for incoming operations");
        return Arrays.stream(ftpClient.listFiles(ftpPath))
                .filter(FTPFile::isFile)
                .filter(file -> file.getName().endsWith(ftpFileSuffix))
                .sorted(Comparator
                        .comparingLong(file -> ((FTPFile) file).getTimestamp().getTimeInMillis())
                        .reversed())
                .limit(1)
                .filter(file -> !lastPdfName.equals(file.getName()))
                .findFirst();
    }

    private static void notifyListeners(Iterable<String> listeners, SimpMessagingTemplate template, OperationDto dto) {
        for (String listener : listeners) {
            logger.debug("🚒 Sending operation to \"" + listener + "\": " + dto.keyword);
            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headerAccessor.setSessionId(listener);
            headerAccessor.setLeaveMutable(true);
            template.convertAndSendToUser(listener, "/notification/operation", dto, headerAccessor.getMessageHeaders());
        }
    }
}
