# PenPath
PenPath class is designed to be used to draw paths with variable thickness. It creates an object of android.graphics.Path and should be drawn with Paint.styles.FILL type paint.

## Features
- Smoothing by averaging last few inputs
- Altering thickness according to direction
- Transform with a matrix without changing thickness

## Path Types
There are two types of PenPath, circles joined by tangents and a sequence of circles.
The first two paths on the picture is drawn by STROKE type paint to demonstrate difference between the two types. The third one is drawn by FILL.

![path types](PathStructuresDemonstration.jpg)
## Constructor  
The constructor of PenPath takes enum PenPath.Type as an argument, members of which are JOIN_WITH_TANGENTS and CIRCLE_SEQUENCE.
PenPath in many ways acts like android.graphics.Path, but the methods lineTo and MoveTo take an additional argument "radius".

## Installation
You can install the package through Gradle by adding:
```
implementation 'com.github.BekaErg:PenPath:v1.0.0'
```
