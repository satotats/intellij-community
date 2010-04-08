package com.jetbrains.python.console;

import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.IconLoader;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.Collections;
import java.util.Map;

/**
 * @author oleg
 */
public class RunPythonConsoleActionNew extends AnAction implements DumbAware {

  public RunPythonConsoleActionNew() {
    super();
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/jetbrains/python/python.png"));
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(false);
    e.getPresentation().setEnabled(false);
    final Project project = e.getData(LangDataKeys.PROJECT);
    if (project != null){
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        e.getPresentation().setVisible(true);
        if (PythonSdkType.findPythonSdk(module) != null){
          e.getPresentation().setEnabled(true);
          break;
        }
      }
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(LangDataKeys.PROJECT);
    assert project != null : "Project is null";
    Sdk sdk = null;
    Module module = null;
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      module = m;
      sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null){
        break;
      }
    }
    assert module != null : "Module is null";
    assert sdk != null : "Sdk is null";

    PydevConsoleRunner.run(project, module, sdk);
  }
}