package com.thumbzup;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "git-commit-id", defaultPhase = LifecyclePhase.INITIALIZE)
public class GitCommitIdMojo extends AbstractMojo {
    private static final String JAVA_CLASS_SOURCE = "package {0};\npublic class {1}'{'\npublic static final String {2} = \"{3}\";\n'}'";

    @Parameter(property = "propertyUpdate", defaultValue = "true")
    private boolean propertyUpdate;
    
    @Parameter(property = "propertyName", defaultValue = "git.commit.id")
    private String propertyName;
    
    @Parameter(property = "javaClassUpdate", defaultValue = "false")
    private boolean javaClassUpdate;

    @Parameter(property = "javaClassName", defaultValue = "com.thumbzup.GitCommitId")
    private String javaClassName;

    @Parameter(property = "javaClassConstant", defaultValue = "GIT_COMMIT_ID")
    private String javaClassConstant;

    /** @parameter default-value="${project}" */
    @Component
    private MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String gitCommitId = getGitCommitId();
        getLog().info("Git Commit ID: " + gitCommitId);
        if(propertyUpdate){
            getLog().info("Updating Maven property.");
            propertyUpdate(gitCommitId);
        }
        if(javaClassUpdate){
            getLog().info("Updating Java file.");
            javaClassUpdate(gitCommitId);
        }
    }
    
    private String getGitCommitId() throws MojoExecutionException {
        CommandUtil.CommandResult commandResult = CommandUtil.executeCommand(getLog(), "git", "rev-parse", "HEAD");

        String[] resultLines = commandResult.getResultLines();
        if (resultLines == null || resultLines.length == 0) {
            return "Unable to get commit id.";
        }

        if (commandResult.getExitCode() != 0) {
            String specificError = "";

            for (String line : resultLines) {
                specificError += line;
            }

            return ("Not expected response code [" + commandResult.getExitCode() + "]. 'git rev-parse HEAD' failed. " + specificError);
        }

        this.getLog().debug("Execute Result: " + commandResult.getExitCode() + commandResult.getResultLines()[0]);

        String returnVal = "";
        for (String line : resultLines) {
            returnVal += line;
        }

        return returnVal;
    }
    
    private void propertyUpdate(String gitCommitId) {
        if (propertyName == null || propertyName.isEmpty()) {
            getLog().error("If 'propertyUpdate' is true, the property 'propertyName' must be a valid name.");
            return;
        }
        Properties properties = mavenProject.getProperties();
        properties.setProperty(propertyName, gitCommitId);
        this.getLog().debug("[" + propertyName + "]: " + gitCommitId);
    }
    
    private void javaClassUpdate(String gitCommitId) {
        if (javaClassName == null || javaClassName.isEmpty()) {
            getLog().error("If 'javaClassUpdate' is true, the property 'javaClassName' must be a valid full class name.");
            return;
        }
        if (javaClassConstant == null || javaClassConstant.isEmpty()) {
            getLog().error("If 'javaClassUpdate' is true, the property 'javaClassConstant' must be a constant variable name.");
            return;
        }

        File javaFile = getJavaFile();

        if (!javaFile.exists()) {
            getLog().info("Java class file '" + javaFile.getAbsolutePath() + "' does not exist. Creating...");
            createJavaFile(javaFile, gitCommitId);
            //no matter what the outcome, nothing to do anymore
            //if true the file is created with the commit id
            //if false we cannot do anything anyway
            return;
        }

        getLog().info("Java class file '" + javaFile.getAbsolutePath() + "' exist. Setting constant '" + javaClassConstant + "'.");
        editJavaFile(javaFile, gitCommitId);
    }
    
    private File getJavaFile() {
        String javaFileName = javaClassName.replace('.', '/');
        File baseDir = this.mavenProject.getBasedir();
        return new File(baseDir, "src/main/java/" + javaFileName + ".java");

    }

    private boolean createJavaFile(File javaFile, String gitCommitId){
        int index = javaClassName.lastIndexOf('.');
        String packageName;
        String javaClassSimpleName;
        if(index == -1){
            //no package
            packageName = "";
            javaClassSimpleName = javaClassName;
        }else{
            packageName = javaClassName.substring(0, index);
            javaClassSimpleName = javaClassName.substring(index + 1);
        }

        String javaClassSource = MessageFormat.format(JAVA_CLASS_SOURCE, packageName, javaClassSimpleName, javaClassConstant, gitCommitId);
        getLog().debug(javaClassSource);
        try{
            FileUtils.writeStringToFile(javaFile, javaClassSource);
            return true;
        }catch(IOException e){
            getLog().error("Failed to create the java class file.", e);
            return false;
        }
    }
    
    private void editJavaFile(File javaFile, String gitCommitId){
        try {
            String javaClassSource = FileUtils.readFileToString(javaFile);
            // public static final String VERSION = "";
            int index = javaClassSource.indexOf(javaClassConstant);
            index = javaClassSource.indexOf("\"", index);
            String prefix = javaClassSource.substring(0, index + 1);
            index = javaClassSource.indexOf("\"", index + 1);
            String postfix = javaClassSource.substring(index);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(prefix);
            stringBuilder.append(gitCommitId);
            stringBuilder.append(postfix);
            this.getLog().debug(stringBuilder);
            
            javaFile.delete();
            FileUtils.writeStringToFile(javaFile, stringBuilder.toString());
        } catch (IOException e) {
            getLog().error("Failed to insert Git Commit ID in java file.", e);
        }
    }
}
