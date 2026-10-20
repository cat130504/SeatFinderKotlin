package com.example.seatfinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

//Tema globale dell'app


private val LightColors = lightColorScheme(
    primary              = BrandBlue,
    onPrimary            = Color.White,
    primaryContainer     = BrandBlue,
    onPrimaryContainer   = Color.White,
    secondary            = BrandBlue,
    onSecondary          = Color.White,
    secondaryContainer   = BrandContainer,
    onSecondaryContainer = OnBrandContainer,
    tertiary             = BrandBlueLight,
    onTertiary           = Color.White,
    tertiaryContainer    = BrandContainer,
    onTertiaryContainer  = OnBrandContainer,
    error                = ErrorRed,
    onError              = OnErrorRed,
    errorContainer       = ErrorContainer,
    onErrorContainer     = OnErrorContainer,
    background           = SurfaceLight,
    onBackground         = OnSurfaceLight,
    surface              = SurfaceLight,
    onSurface            = OnSurfaceLight,
    surfaceVariant       = SurfaceVariantLight,
    onSurfaceVariant     = OnSurfaceVariantLight,
    outline              = OutlineLight,
)

// Wrapper unico del tema: va chiamato una volta per schermata
@Composable
fun SeatFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}