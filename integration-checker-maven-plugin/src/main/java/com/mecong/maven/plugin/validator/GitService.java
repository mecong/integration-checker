package com.mecong.maven.plugin.validator;

import lombok.SneakyThrows;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Named
@Singleton
public class GitService {

    public String getCurrentGitBranch() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec("git rev-parse --abbrev-ref HEAD");
        process.waitFor();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        return reader.readLine();
    }


    @SneakyThrows
    public boolean isCurrentGitBranchForbidden(List<String> allowedGitBranches, String parentDir, Log log) {
        if (allowedGitBranches == null || allowedGitBranches.isEmpty()) return false;

        try {
            return analiseGitBranch(allowedGitBranches, getFullGitBranchName(parentDir, log));
        } catch (RepositoryNotFoundException e) {
            log.warn("No git repository found. Exiting.");
            return true;
        }
    }

    private String getFullGitBranchName(String parentDir, Log log) throws IOException {
        String fullGitBranch;
        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
        repositoryBuilder.setMustExist(true);
        repositoryBuilder.setWorkTree(new File(parentDir));
        log.debug("Checking git branch at: " + parentDir);
        Repository repository = repositoryBuilder.build();
        fullGitBranch = repository.getFullBranch();
        log.debug("Current git branch: " + fullGitBranch);
        return fullGitBranch;
    }

    private boolean analiseGitBranch(List<String> allowedGitBranches, String fullGitBranch) {
        if (fullGitBranch == null) {
            return true;
        } else {
            String gitBranchShort = getShortBranchName(fullGitBranch);
            Optional<String> any = allowedGitBranches.stream().filter(s -> {
                Pattern pattern = Pattern.compile(s);
                Matcher matcher = pattern.matcher(gitBranchShort);
                return matcher.matches();
            }).findAny();

            return !any.isPresent();
        }
    }

    private String getShortBranchName(String fullGitBranch) {
        String[] gitBranchChunks = fullGitBranch.split("/");
        return gitBranchChunks[gitBranchChunks.length - 1];
    }
}
