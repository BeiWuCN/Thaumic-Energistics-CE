# `src/main/java/thaumicenergistics_ce/client/render/bubble/OccultMonitorBubbleRenderState.java`

以下为迁移前的代码注释。行号指迁移前源码位置；注释内容保留原文。

## 原第 7 行

````java
/**
 * 气泡渲染器从 extract 阶段传给 submit 阶段的东西。
 * <p>
 * 量面板要字体、还要看一眼机器，画它要相机；新的方块实体渲染器模型把两件事分在两个阶段做，
 * 所以行和它们加出来的方框存在这里。{@code rows} 为 null 表示这一帧没东西可画。
 */
````

## 原第 15 行

````java
/** 排好版的行；机器没在报告、面板没建出来时为 null。 */
````

## 原第 19 行

````java
/** 面板尺寸，单位是面板单位，含内边距；只在 extract 阶段量一次。 */
````
