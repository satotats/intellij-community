/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2006
 * Time: 19:59:36
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.text.CharArrayCharSequence;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.io.*;
import java.util.*;

public class ShelveChangesManager implements ProjectComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager");

  @NonNls private static final String PATCH_EXTENSION = "patch";

  public static ShelveChangesManager getInstance(Project project) {
    return project.getComponent(ShelveChangesManager.class);
  }

  private Project myProject;
  private MessageBus myBus;
  private List<ShelvedChangeList> myShelvedChangeLists = new ArrayList<ShelvedChangeList>();
  @NonNls private static final String ELEMENT_CHANGELIST = "changelist";
  @NonNls private String myShelfPath;

  public static final Topic<ChangeListener> SHELF_TOPIC = new Topic<ChangeListener>("shelf updates", ChangeListener.class);

  public ShelveChangesManager(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public void projectOpened() {
    myShelfPath = PathManager.getConfigPath() + File.separator + "shelf";
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "ShelveChangesManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    //noinspection unchecked
    final List<Element> children = (List<Element>)element.getChildren(ELEMENT_CHANGELIST);
    for(Element child: children) {
      ShelvedChangeList data = new ShelvedChangeList();
      data.readExternal(child);
      if (new File(data.PATH).exists()) {
        myShelvedChangeLists.add(data);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for(ShelvedChangeList data: myShelvedChangeLists) {
      Element child = new Element(ELEMENT_CHANGELIST);
      data.writeExternal(child);
      element.addContent(child);
    }
  }

  public List<ShelvedChangeList> getShelvedChangeLists() {
    return Collections.unmodifiableList(myShelvedChangeLists);
  }

  public void shelveChanges(final Collection<Change> changes, final String commitMessage) throws IOException, VcsException {
    final List<Change> textChanges = new ArrayList<Change>();
    final List<ShelvedBinaryFile> binaryFiles = new ArrayList<ShelvedBinaryFile>();
    for(Change change: changes) {
      if (change.getBeforeRevision() instanceof BinaryContentRevision || change.getAfterRevision() instanceof BinaryContentRevision) {
        binaryFiles.add(shelveBinaryFile(change));
      }
      else {
        textChanges.add(change);
      }
    }

    File patchPath = getPatchPath(commitMessage);
    Writer writer;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(patchPath));
    }
    catch(IOException ex) {
      patchPath = getPatchPath("shelved_change");
      writer = new OutputStreamWriter(new FileOutputStream(patchPath));
    }
    try {
      List<FilePatch> patches = PatchBuilder.buildPatch(textChanges, myProject.getBaseDir().getPresentableUrl(), true);
      UnifiedDiffWriter.write(patches, writer);
    }
    finally {
      writer.close();
    }

    RollbackChangesDialog.doRollback(myProject, changes, true, false);

    myShelvedChangeLists.add(new ShelvedChangeList(patchPath.toString(), commitMessage.replace('\n', ' '), binaryFiles));
    notifyStateChanged();
  }

  private ShelvedBinaryFile shelveBinaryFile(final Change change) throws IOException {
    final ContentRevision beforeRevision = change.getBeforeRevision();
    final ContentRevision afterRevision = change.getAfterRevision();
    File beforeFile = beforeRevision == null ? null : beforeRevision.getFile().getIOFile();
    File afterFile = afterRevision == null ? null : afterRevision.getFile().getIOFile();
    String shelvedPath = null;
    if (afterFile != null) {
      String shelvedName = FileUtil.getNameWithoutExtension(afterFile.getName());
      String shelvedExt = FileUtil.getExtension(afterFile.getName());
      File shelvedFile = FileUtil.findSequentNonexistentFile(new File(myShelfPath), shelvedName, shelvedExt);

      FileUtil.copy(afterRevision.getFile().getIOFile(), shelvedFile);
      shelvedPath = shelvedFile.getPath();
    }
    File baseDirFile = new File(myProject.getBaseDir().getPath());
    String beforePath = beforeFile == null ? null : FileUtil.getRelativePath(baseDirFile, beforeFile);
    String afterPath = afterFile == null ? null : FileUtil.getRelativePath(baseDirFile, afterFile);
    return new ShelvedBinaryFile(beforePath, afterPath, shelvedPath);
  }

  private void notifyStateChanged() {
    myBus.syncPublisher(SHELF_TOPIC).stateChanged(new ChangeEvent(this));
  }

  private File getPatchPath(final String commitMessage) {
    File file = new File(myShelfPath);
    if (!file.exists()) {
      file.mkdirs();
    }

    return suggestPatchName(commitMessage, file);
  }

  public static File suggestPatchName(final String commitMessage, final File file) {
    @NonNls String defaultPath = commitMessage.replace(' ', '_').replace('.', '_').replace(File.separatorChar, '_').replace('\t', '_').replace('\n', '_').replace(':', '_').replace('/', '_');
    if (defaultPath.length() == 0) {
      defaultPath = "unnamed";
    }
    return FileUtil.findSequentNonexistentFile(file, defaultPath, PATCH_EXTENSION);
  }

  public void unshelveChangeList(final ShelvedChangeList changeList, @Nullable final List<ShelvedChange> changes,
                                 @Nullable final List<ShelvedBinaryFile> binaryFiles) {
    List<FilePatch> remainingPatches = new ArrayList<FilePatch>();
    try {
      List<FilePatch> patches = loadPatches(changeList.PATH);
      if (changes != null) {
        final Iterator<FilePatch> iterator = patches.iterator();
        while (iterator.hasNext()) {
          FilePatch patch = iterator.next();
          if (!needUnshelve(patch, changes)) {
            remainingPatches.add(patch);
            iterator.remove();
          }
        }
      }
      List<VirtualFile> filesToMakeWritable = new ArrayList<VirtualFile>();
      VirtualFile baseDir = myProject.getBaseDir();
      if (!ApplyPatchAction.prepareFiles(myProject, patches, baseDir, 0, filesToMakeWritable)) {
        return;
      }

      List<ShelvedBinaryFile> binaryFilesToUnshelve = getBinaryFilesToUnshelve(changeList, binaryFiles);
      for(ShelvedBinaryFile file: binaryFilesToUnshelve) {
        if (file.BEFORE_PATH != null) {
          final String beforePath = file.BEFORE_PATH == null ? null : file.BEFORE_PATH.replace(File.separatorChar, '/');
          final String afterPath = file.AFTER_PATH == null ? null : file.AFTER_PATH.replace(File.separatorChar, '/');
          final boolean isNewFile = beforePath == null;
          VirtualFile patchTarget = FilePatch.findPatchTarget(myProject.getBaseDir(), beforePath, afterPath, 0, isNewFile, true, true);
            if (patchTarget != null) {
            filesToMakeWritable.add(patchTarget);
          }
        }
      }

      final VirtualFile[] fileArray = filesToMakeWritable.toArray(new VirtualFile[filesToMakeWritable.size()]);
      final ReadonlyStatusHandler.OperationStatus readonlyStatus = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(fileArray);
      if (readonlyStatus.hasReadonlyFiles()) {
        return;
      }

      if (ApplyPatchAction.applyFilePatches(myProject, patches, baseDir, 0, true, true) == ApplyPatchStatus.FAILURE) {
        return;
      }
      for(ShelvedBinaryFile file: binaryFilesToUnshelve) {
        boolean success = unshelveBinaryFile(file);
        if (!success) {
          break;
        }
        changeList.getBinaryFiles().remove(file);
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    catch (PatchSyntaxException e) {
      LOG.error(e);
      return;
    }
    if (remainingPatches.size() == 0 && changeList.getBinaryFiles().size() == 0) {
      deleteChangeList(changeList);
    }
    else {
      saveRemainingPatches(changeList, remainingPatches);
    }
  }

  private static List<ShelvedBinaryFile> getBinaryFilesToUnshelve(final ShelvedChangeList changeList, final List<ShelvedBinaryFile> binaryFiles) {
    if (binaryFiles == null) {
      return new ArrayList<ShelvedBinaryFile>(changeList.getBinaryFiles());
    }
    ArrayList<ShelvedBinaryFile> result = new ArrayList<ShelvedBinaryFile>();
    for(ShelvedBinaryFile file: changeList.getBinaryFiles()) {
      if (binaryFiles.contains(file)) {
        result.add(file);
      }
    }
    return result;
  }

  private boolean unshelveBinaryFile(final ShelvedBinaryFile file) throws IOException {
    final String beforePath = file.BEFORE_PATH == null ? null : file.BEFORE_PATH.replace(File.separatorChar, '/');
    final String afterPath = file.AFTER_PATH == null ? null : file.AFTER_PATH.replace(File.separatorChar, '/');
    final boolean isNewFile = beforePath == null;
    final VirtualFile patchTarget = FilePatch.findPatchTarget(myProject.getBaseDir(), beforePath, afterPath, 0, isNewFile, true, true);
    if (patchTarget != null) {
      final Ref<IOException> ex = new Ref<IOException>();
      final File shelvedFile = new File(file.SHELVED_PATH);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          VirtualFile fileToPatch = patchTarget;
          try {
            if (isNewFile) {
              fileToPatch = fileToPatch.createChildData(this, new File(afterPath).getName());
            }
            fileToPatch.setBinaryContent(FileUtil.loadFileBytes(shelvedFile));
          }
          catch (IOException e) {
            ex.set(e);
          }
        }
      });
      if (!ex.isNull()) {
        throw ex.get();
      }
      FileUtil.delete(shelvedFile);
      return true;
    }
    else {
      Messages.showErrorDialog(myProject, "Failed to unshelve binary file " + (afterPath != null ? afterPath : beforePath),
        "Unshelve Changes");
      return false;
    }
  }

  private static boolean needUnshelve(final FilePatch patch, final List<ShelvedChange> changes) {
    for(ShelvedChange change: changes) {
      if (Comparing.equal(patch.getBeforeName(), change.getBeforePath())) {
        return true;
      }
    }
    return false;
  }

  private void saveRemainingPatches(final ShelvedChangeList changeList, final List<FilePatch> remainingPatches) {
    OutputStreamWriter writer;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(changeList.PATH));
      try {
        UnifiedDiffWriter.write(remainingPatches, writer);
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    changeList.clearLoadedChanges();
    notifyStateChanged();
  }

  public void deleteChangeList(final ShelvedChangeList changeList) {
    FileUtil.delete(new File(changeList.PATH));
    for(ShelvedBinaryFile binaryFile: changeList.getBinaryFiles()) {
      FileUtil.delete(new File(binaryFile.SHELVED_PATH));
    }
    myShelvedChangeLists.remove(changeList);
    notifyStateChanged();
  }

  public static List<FilePatch> loadPatches(final String patchPath) throws IOException, PatchSyntaxException {
    char[] text = FileUtil.loadFileText(new File(patchPath));
    PatchReader reader = new PatchReader(new CharArrayCharSequence(text));
    return reader.readAllPatches();
  }

}