import 'package:flutter/services.dart';


class MapAssets {
  static const fallback = 'assets/maps/q145.png';
  static List<String>? _cache;

  static String _norm(String s) => s.trim().toLowerCase().replaceAll(' ', '_');

  static String _baseName(String path) {
    final file = path.split('/').last;
    final dot = file.lastIndexOf('.');
    return dot == -1 ? file : file.substring(0, dot);
  }

  static Future<String> resolve(String edificio, String quota) async {
    _cache ??= (await AssetManifest.loadFromAssetBundle(rootBundle))
        .listAssets()
        .where((a) => a.startsWith('assets/maps/'))
        .toList();

    for (final nome in ['${_norm(edificio)}_${_norm(quota)}', _norm(quota)]) {
      for (final a in _cache!) {
        if (_baseName(a).toLowerCase() == nome) return a;
      }
    }
    return fallback;
  }
}
