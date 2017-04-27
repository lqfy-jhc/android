/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.module;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.idea.gradle.npw.project.GradleAndroidProjectPaths;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.npw.template.ChooseActivityTypeStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_INCLUDE_FORM_FACTOR;
import static org.jetbrains.android.util.AndroidBundle.message;


public class ConfigureAndroidModuleStep extends SkippableWizardStep<NewModuleModel> {
  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private ValidatorPanel myValidatorPanel;
  @NotNull private final FormFactor myFormFactor;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final int myMinSdkLevel;
  private final boolean myIsLibrary;
  private final boolean myIsInstantApp;

  private FormFactorApiComboBox mySdkControls;
  private JTextField myModuleName;
  private JPanel myPanel;
  private JTextField myAppName;
  private LabelWithEditButton myPackageName;

  @NotNull private RenderTemplateModel myRenderModel;

  public ConfigureAndroidModuleStep(@NotNull NewModuleModel model, @NotNull FormFactor formFactor, int minSdkLevel,
                                    boolean isLibrary, boolean isInstantApp, @NotNull String title) {
    super(model, title);

    myFormFactor = formFactor;
    myMinSdkLevel = minSdkLevel;
    myIsLibrary = isLibrary;
    myIsInstantApp = isInstantApp;

    StringProperty companyDomain = new StringValueProperty(NewProjectModel.getInitialDomain(false));
    TextProperty packageNameText = new TextProperty(myPackageName);
    TextProperty moduleNameText = new TextProperty(myModuleName);

    Expression<String> computedPackageName = new DomainToPackageExpression(companyDomain, model.applicationName());
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    // Project should never be null (we are adding a new module to an existing project)
    NewModuleModel moduleModel = getModel();
    Project project = moduleModel.getProject().getValue();

    Expression<String> computedModuleName = new AppNameToModuleNameExpression(project, model.applicationName());
    BoolProperty isModuleNameSynced = new BoolValueProperty(true);
    myBindings.bind(moduleNameText, computedModuleName, isModuleNameSynced);
    myBindings.bind(model.moduleName(), moduleNameText);
    myListeners.receive(moduleNameText, value -> isModuleNameSynced.set(value.equals(computedModuleName.get())));

    myBindings.bindTwoWay(new TextProperty(myAppName), model.applicationName());

    myValidatorPanel = new ValidatorPanel(this, myPanel);

    myValidatorPanel.registerValidator(model.applicationName(), value -> {
      if (value.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.validate.empty.application.name"));
      }
      else if (!model.isLibrary().get() && !Character.isUpperCase(value.charAt(0))) {
        return new Validator.Result(Validator.Severity.INFO,  message("android.wizard.validate.lowercase.application.name"));
      }
      return Validator.Result.OK;
    });

    myValidatorPanel.registerValidator(model.moduleName(), new ModuleValidator(project));
    myValidatorPanel.registerValidator(model.packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    AndroidSourceSet dummySourceSet = GradleAndroidProjectPaths.createDummySourceSet();

    myRenderModel = new RenderTemplateModel(moduleModel, null, dummySourceSet, message("android.wizard.activity.add", myFormFactor.id));

    myBindings.bind(myRenderModel.androidSdkInfo(), new SelectedItemProperty<>(mySdkControls));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, message("android.wizard.module.config.title"));
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    // Note: MultiTemplateRenderer needs that all Models constructed (ie myRenderModel) are inside a Step, so handleSkipped() is called
    ChooseActivityTypeStep chooseActivityStep = new ChooseActivityTypeStep(getModel(), myRenderModel, myFormFactor, Lists.newArrayList());
    chooseActivityStep.setShouldShow(!myIsLibrary || myIsInstantApp);
    return Lists.newArrayList(chooseActivityStep);
  }

  @Override
  protected void onEntering() {
    // TODO: 1 - In the old version, the combo box was initially populated with local data, and then later (asynchronously) with remote data
    // now, the init is only called when all data is loaded, so the combo box stays empty for longer
    // 2 - The old version only loaded the list of version once, and kept everything on a static field
    // Possible solutions: Move AndroidVersionsInfo/load to the class that instantiates this step?
    // Add a new method to androidVersionsInfo.ItemsLoaded interface: onDataLoadedStarted(List<VersionItem> items) that provides the already
    // loaded Local Store items?
    AndroidVersionsInfo androidVersionsInfo = new AndroidVersionsInfo();
    androidVersionsInfo.load();

    androidVersionsInfo.loadTargetVersions(myFormFactor, myMinSdkLevel, items -> mySdkControls.init(myFormFactor, items));
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    NewModuleModel moduleModel = getModel();
    moduleModel.getTemplateValues().put(myFormFactor.id + ATTR_INCLUDE_FORM_FACTOR, true);

    // At this point, the validator panel should have no errors, and the user has typed a valid Module Name
    getModel().moduleName().set(myModuleName.getText());
    File moduleRoot = new File(moduleModel.moduleName().get(), moduleModel.getProject().getValue().getBasePath());
    myRenderModel.getSourceSet().set(GradleAndroidProjectPaths.createDefaultSourceSetAt(moduleRoot));

    if (myIsLibrary) {
      moduleModel.setDefaultRenderTemplateValues(myRenderModel);

      new TemplateValueInjector(moduleModel.getTemplateValues())
        .setProjectDefaults(moduleModel.getProject().getValueOrNull(), moduleModel.applicationName().get(), myIsInstantApp);
    }
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  private void createUIComponents() {
    mySdkControls = new FormFactorApiComboBox();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
