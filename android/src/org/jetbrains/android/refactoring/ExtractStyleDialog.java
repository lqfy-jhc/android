// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.refactoring;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ExtractStyleDialog extends DialogWrapper {

  private JPanel myPanel;
  private JTextField myStyleNameField;
  private JPanel myAttributeListWrapper;
  private JBLabel myAttributesLabel;
  private JBLabel myModuleLabel;
  private ModulesComboBox myModuleCombo;
  private JBCheckBox mySearchForStyleApplicationsAfter;

  private final Module myModule;
  private final String myFileName;
  private final List<String> myDirNames;

  private final CheckboxTree myTree;
  private final CheckedTreeNode myRootNode;

  private static final String SEARCH_STYLE_APPLICATIONS_PROPERTY = "AndroidExtractStyleSearchStyleApplications";

  public ExtractStyleDialog(@NotNull Module module,
                            @NotNull String fileName,
                            @Nullable String parentStyleName,
                            @NotNull List<String> dirNames,
                            @NotNull List<XmlAttribute> attributes) {
    super(module.getProject());
    myFileName = fileName;
    myDirNames = dirNames;

    if (parentStyleName != null && !parentStyleName.isEmpty()) {
      myStyleNameField.setText(parentStyleName + ".");
      myStyleNameField.selectAll();
    } else {
      String prefix = IdeResourcesUtil.prependResourcePrefix(module, null, ResourceFolderType.VALUES);
      if (prefix != null) {
        myStyleNameField.setText(prefix);
      }
    }

    final Set<Module> modulesSet = new HashSet<Module>();
    modulesSet.add(module);

    for (AndroidFacet depFacet : AndroidDependenciesCache.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    assert !modulesSet.isEmpty();

    if (modulesSet.size() == 1) {
      myModule = module;
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    else {
      myModule = null;
      myModuleCombo.setModules(modulesSet);
      myModuleCombo.setSelectedModule(module);
    }

    myRootNode = new CheckedTreeNode(null);

    for (XmlAttribute attribute : attributes) {
      myRootNode.add(new CheckedTreeNode(attribute));
    }

    CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof CheckedTreeNode) {
          XmlAttribute attribute = (XmlAttribute)((CheckedTreeNode)value).getUserObject();
          if (attribute != null) {
            getTextRenderer().append(attribute.getLocalName());
            getTextRenderer().append(" [" + attribute.getValue() + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    };
    myTree = new CheckboxTree(renderer, myRootNode) {
      @Override
      protected void installSpeedSearch() {
        TreeSpeedSearch.installOn(this, false, new Function<TreePath, String>() {
          @Override
          public String apply(TreePath path) {
            Object object = path.getLastPathComponent();
            if (object instanceof CheckedTreeNode) {
              XmlAttribute attribute = (XmlAttribute)((CheckedTreeNode)object).getUserObject();
              if (attribute != null) {
                return attribute.getLocalName();
              }
            }
            return "";
          }
        });
      }
    };
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    TreeUtil.expandAll(myTree);

    myAttributesLabel.setLabelFor(myTree);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree);
    decorator.setToolbarPosition(ActionToolbarPosition.RIGHT);
    decorator.setEditAction(null);
    decorator.disableUpDownActions();

    AnActionButton selectAll = new AnActionButton(AndroidBundle.messagePointer("action.AnActionButton.extract.style.text.select.all"), PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        setChecked(true);
      }
    };
    decorator.addExtraAction(selectAll);

    AnActionButton unselectAll = new AnActionButton(AndroidBundle.messagePointer("action.AnActionButton.extract.style.text.unselect.all"), PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        setChecked(false);
      }
    };
    decorator.addExtraAction(unselectAll);

    myAttributeListWrapper.add(decorator.createPanel());

    final String value = PropertiesComponent.getInstance().getValue(SEARCH_STYLE_APPLICATIONS_PROPERTY);
    mySearchForStyleApplicationsAfter.setSelected(Boolean.parseBoolean(value));
    init();
  }

  private void setChecked(boolean value) {
    int count = myRootNode.getChildCount();

    for (int i = 0; i < count; i++) {
      ((CheckedTreeNode)myRootNode.getChildAt(i)).setChecked(value);
    }
    myTree.repaint();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    PropertiesComponent.getInstance().setValue(
      SEARCH_STYLE_APPLICATIONS_PROPERTY, Boolean.toString(mySearchForStyleApplicationsAfter.isSelected()));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStyleNameField;
  }

  @Override
  protected ValidationInfo doValidate() {
    final String styleName = getStyleName();

    if (styleName.isEmpty()) {
      return new ValidationInfo("specify style name", myStyleNameField);
    }
    if (!IdeResourcesUtil.isCorrectAndroidResourceName(styleName)) {
      return new ValidationInfo("incorrect style name", myStyleNameField);
    }
    final Module module = getChosenModule();
    if (module == null) {
      return new ValidationInfo("specify module", myModuleCombo);
    }
    VirtualFile resourceDir = getResourceDirectory();
    if (resourceDir == null) {
      return new ValidationInfo("specify a module with resources", myModuleCombo);
    }

    return CreateXmlResourceDialog.checkIfResourceAlreadyExists(module.getProject(), resourceDir, getStyleName(), null, ResourceType.STYLE,
                                                                myDirNames, myFileName);
  }

  @NotNull
  public String getStyleName() {
    return myStyleNameField.getText().trim();
  }

  @NotNull
  public List<XmlAttribute> getStyledAttributes() {
    List<XmlAttribute> attributes = new ArrayList<XmlAttribute>();
    int count = myRootNode.getChildCount();

    for (int i = 0; i < count; i++) {
      final CheckedTreeNode treeNode = (CheckedTreeNode)myRootNode.getChildAt(i);

      if (treeNode.isChecked()) {
        attributes.add((XmlAttribute)treeNode.getUserObject());
      }
    }
    return attributes;
  }

  @Nullable
  private Module getChosenModule() {
    return myModule != null ? myModule : myModuleCombo.getSelectedModule();
  }

  @Nullable
  public VirtualFile getResourceDirectory() {
    Module module = getChosenModule();
    if (module == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    return ResourceFolderManager.getInstance(facet).getPrimaryFolder();
  }

  public boolean isToSearchStyleApplications() {
    return mySearchForStyleApplicationsAfter.isSelected();
  }
}