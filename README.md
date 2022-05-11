# PenPath
PenPath class is designed be used to draw paths with variable thickness. It creates an object of android.graphics.Path and which should be drawn with FILL type paint.

## Features
- Smoothing by averageing last few inputs
- Altering thickness according to direction
- Transform with matrix without changing thickness
## Path Types
There are two types of PenPath, circles joined by tangents or just sequence of circles.
The first two paths on the picture is drawn by STROKE type paint to demonstrate difference between the two types. The third one is drawn by FILL.

![path types](PathStructuresDemonstration.jpg)
## Constructor  
The constructor of PenPath takes enum PenPath.Type as an argument, members of which are JOIN_WITH_TANGENTS and CIRCLE_SEQUENCE.
PenPath in many ways acts like android.graphics.Path, but the methods lineTo and MoveTo take third argument radius.

## Instalation
You can install the package through Gradle by adding:
```
implementation 'com.github.BekaErg:PenPath:Tag'
```
