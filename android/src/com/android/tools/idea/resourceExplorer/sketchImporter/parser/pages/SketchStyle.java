/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages;

import java.awt.Color;
import java.awt.Shape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SketchStyle {
  public final SketchGraphicContextSettings DEFAULT_CONTEXT_SETTINGS = new SketchGraphicContextSettings((short)0, (short)1);
  public final static short DEFAULT_BLENDING_MODE = 0;

  private final SketchBorderOptions borderOptions;
  private final SketchBorder[] borders;
  /**
   * If this field does not exist, the default values are considered:
   * <ul>
   * <li>blendMode: 0</li>
   * <li>opacity: 1</li>
   * </ul>
   */
  private SketchGraphicContextSettings contextSettings;
  private final SketchColorControls colorControls;
  private final SketchFill[] fills;
  private final short miterLimit;
  private final SketchShadow[] shadows;
  private final short windingRule;

  public SketchStyle(@Nullable SketchBorderOptions borderOptions,
                     @Nullable SketchBorder[] borders,
                     @Nullable SketchGraphicContextSettings contextSettings,
                     @Nullable SketchColorControls colorControls,
                     @Nullable SketchFill[] fills,
                     short miterLimit,
                     @Nullable SketchShadow[] shadows,
                     short windingRule) {
    this.borderOptions = borderOptions;
    this.borders = borders;
    this.colorControls = colorControls;
    this.contextSettings = contextSettings != null ? contextSettings : DEFAULT_CONTEXT_SETTINGS;
    this.fills = fills;
    this.miterLimit = miterLimit;
    this.shadows = shadows;
    this.windingRule = windingRule;
  }

  @Nullable
  public SketchBorderOptions getBorderOptions() {
    return borderOptions;
  }

  @Nullable
  public SketchBorder[] getBorders() {
    return borders;
  }

  @Nullable
  public SketchGraphicContextSettings getContextSettings() {
    return contextSettings;
  }

  @Nullable
  public SketchColorControls getColorControls() {
    return colorControls;
  }

  @Nullable
  public SketchFill[] getFills() {
    return fills;
  }

  public short getMiterLimit() {
    return miterLimit;
  }

  @Nullable
  public SketchShadow[] getShadows() {
    return shadows;
  }

  public short getWindingRule() {
    return windingRule;
  }

  public void setFill(@NotNull SketchFill fill) {
    if (getFills() != null && getFills().length != 0) {
      getFills()[0] = fill;
    }
  }

  /**
   * This method applies the overall opacity of the group on its fill and border, by modifying
   * the existing {@link SketchGraphicContextSettings} object, or creating a new one, if it doesn't exist
   *
   * @param parentOpacity
   */
  public void applyParentOpacity(double parentOpacity) {
    SketchFill[] shapeFills = getFills();
    SketchBorder[] shapeBorders = getBorders();
    if (contextSettings != null) {
      contextSettings.addOpacity(parentOpacity);
    }
    else {
      contextSettings = new SketchGraphicContextSettings(DEFAULT_BLENDING_MODE, parentOpacity);
    }

    if (shapeFills != null) {
      SketchFill fill = shapeFills[0];
      if (fill != null) {
        fill.applyGraphicContextSettings(contextSettings);
      }
    }
    if (shapeBorders != null) {
      SketchBorder border = shapeBorders[0];
      if (border != null) {
        border.applyGraphicContextSettings(contextSettings);
      }
    }
  }

  /**
   * This method transforms the gradient's coordinates from percentages to coordinates
   * relative to the shape itself. The coordinates, however, are not absolute because the
   * shape might need extra translations.
   *
   * @param shape
   */
  public void makeGradientRelative(@NotNull Shape shape) {
    SketchFill[] shapeFills = getFills();
    SketchFill shapeFill = shapeFills != null && shapeFills.length != 0 ? shapeFills[0] : null;
    SketchGradient shapeGradient = shapeFill != null ? shapeFill.getGradient() : null;
    if (shapeGradient != null) {
      shapeGradient.toRelativeGradient(shape.getBounds2D());
    }
  }

  @NotNull
  protected static Color addAlpha(Color color, double opacity) {
    //noinspection UseJBColor
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(color.getAlpha() * opacity));
  }
}
