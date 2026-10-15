import 'package:flutter/material.dart';

// --- Brand ---
const brandBlue = Color(0xFF0A5B95);
const brandBlueLight = Color(0xFF4A8BC2);
const brandContainer = Color(0xFFD1E4FF);
const onBrandContainer = Color(0xFF001D36);

// --- Error ---
const errorRed = Color(0xFFBA1A1A);
const errorContainerColor = Color(0xFFFFDAD6);
const onErrorContainerColor = Color(0xFF410002);

// --- Superfici ---
const surfaceLight = Color(0xFFFCFCFF);
const onSurfaceLight = Color(0xFF1A1C1E);
const surfaceVariantLight = Color(0xFFDFE2EB);
const onSurfaceVariantLight = Color(0xFF43474E);
const outlineLight = Color(0xFF73777F);

/// Tema unico, solo light (come in Kotlin).
ThemeData buildTheme() {
  const scheme = ColorScheme.light(
    primary: brandBlue,
    onPrimary: Colors.white,
    primaryContainer: brandBlue, // voluto: tessere/blu piene
    onPrimaryContainer: Colors.white,
    secondary: brandBlue,
    onSecondary: Colors.white,
    secondaryContainer: brandContainer,
    onSecondaryContainer: onBrandContainer,
    tertiary: brandBlueLight,
    onTertiary: Colors.white,
    tertiaryContainer: brandContainer,
    onTertiaryContainer: onBrandContainer,
    error: errorRed,
    onError: Colors.white,
    errorContainer: errorContainerColor,
    onErrorContainer: onErrorContainerColor,
    surface: surfaceLight,
    onSurface: onSurfaceLight,
    surfaceContainerHighest: surfaceVariantLight,
    onSurfaceVariant: onSurfaceVariantLight,
    outline: outlineLight,
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: surfaceLight,
  );
}
