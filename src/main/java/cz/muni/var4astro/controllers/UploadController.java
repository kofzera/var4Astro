package cz.muni.var4astro.controllers;

import cz.muni.var4astro.dao.UploadErrorMessagesDaoImpl;
import cz.muni.var4astro.dao.UploadLogsDaoImpl;
import cz.muni.var4astro.dto.User;
import cz.muni.var4astro.exceptions.CsvContentException;
import cz.muni.var4astro.services.FileHandlingService;
import cz.muni.var4astro.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Controller
@RequestMapping("/upload")
public class UploadController {

    @Autowired
    FileHandlingService fileHandlingService;

    @Autowired
    UserService userService;

    @Autowired
    UploadLogsDaoImpl uploadLogsDao;

    @Autowired
    UploadErrorMessagesDaoImpl uploadErrorMessagesDao;

    @GetMapping("")
    public String showAboutPage() {
        return "upload";
    }

    static final long TIMEOUT_INFINITY = -1L;

    @PostMapping("/save")
    @ResponseBody
    public String storeUploadedFiles(@RequestParam(name = "file") MultipartFile file,
                                     @RequestParam(name = "dir-name") String dirName)
            throws IOException {
        String path;
        if (dirName.equals("create_new")) {
            path = Files.createTempDirectory(
                    Path.of(System.getProperty("java.io.tmpdir")), "flux").toString();
        } else {
            path = dirName;
        }
        File normalFile = new File(path +
                "/" + file.getOriginalFilename());
        file.transferTo(normalFile);
        return path;
    }

    @GetMapping("/parse")
    public SseEmitter parseAndPersist(@RequestParam(name = "path-to-dir") String pathToDir,
                                      @RequestParam(name = "file-count") int numOfFiles) {
        // need to get user beforehand because security context is lost in a new thread
        User uploadingUser = userService.getCurrentUser();
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        List<Pair<String, String>> fileErrorMessagePairsList = new ArrayList<>();
        AtomicInteger unsuccessfulCount = new AtomicInteger();
        SseEmitter emitter = new SseEmitter(TIMEOUT_INFINITY);
        ExecutorService sseExecutor = Executors.newSingleThreadExecutor();
        // separate thread because of SseEmitter
        sseExecutor.execute(() -> {
            try {
                if (!Files.isDirectory(Paths.get(pathToDir))) {
                    throw new FileNotFoundException("Given path to the directory is not correct.");
                }
//                 Prevent getting path outside /tmp, comment in case of testing
                if (!pathToDir.matches("/tmp/[^/]*")) {
                    throw new FileNotFoundException("Given path to the directory is not correct.");
                }
                try (Stream<Path> filePaths = Files.walk(Paths.get(pathToDir))) {
                    List<Path> regularFiles = filePaths
                            .filter(Files::isRegularFile)
                            .collect(Collectors.toList());
                    for (Path file : regularFiles) {
                        try {
                            fileHandlingService.parseAndPersist(file, uploadingUser);
                            emitter.send(SseEmitter.event()
                                    .name("FILE_STORED")
                                    .data(file.getFileName()));
                        } catch (IOException | CsvContentException e) {
                            unsuccessfulCount.getAndIncrement();
                            fileErrorMessagePairsList.add(Pair.of(
                                    file.getFileName().toString(), e.getMessage()));
                        }
                    }
                    emitter.send(SseEmitter.event()
                            .name("COMPLETED")
                            .data(unsuccessfulCount));
                    emitter.complete();
                } catch (IOException e) {
                    logUploadData(uploadingUser, currentTime, numOfFiles, numOfFiles,
                            new ArrayList<>());
                    emitter.completeWithError(e);
                }
                logUploadData(uploadingUser, currentTime, numOfFiles,
                        unsuccessfulCount.get(), fileErrorMessagePairsList);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        sseExecutor.shutdown();
        return emitter;
    }

    private void logUploadData(User uploadingUser, Timestamp uploadTime, int numOfFiles,
                               int numOfErrors, List<Pair<String, String>> fileErrorMessagePairsList) {
        long logId = uploadLogsDao.saveUploadLog(uploadingUser, uploadTime, numOfFiles, numOfErrors);
        for (Pair<String, String> errorMessagePair : fileErrorMessagePairsList) {
            uploadErrorMessagesDao.saveUploadErrorMessage(logId, errorMessagePair);
        }
    }
}