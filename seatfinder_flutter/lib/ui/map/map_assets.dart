import 'package:flutter/services.dart';


//classe di utilità per risolvere dinamicamente il percorso degli asset delle planimetrie
class MapAssets {
  static const fallback = 'assets/maps/q145.png';
  static List<String>? _cache;

  static String _norm(String s) => s.trim().toLowerCase().replaceAll(' ', '_');

  static String _baseName(String path) {
    final file = path.split('/').last;
    final dot = file.lastIndexOf('.');
    return dot == -1 ? file : file.substring(0, dot);
  }
  //metodo asincrono per risolvere l'asset corretto in base al nome dell'edificio e della quota (piano)
  static Future<String> resolve(String edificio, String quota) async {
    //carica la lista degli asset dal bundle solo la prima volta (inizializzando la cache)
    _cache ??= (await AssetManifest.loadFromAssetBundle(rootBundle))
        .listAssets()
        .where((a) => a.startsWith('assets/maps/'))
        .toList();
    //cerca prima combinando edificio e quota normalizzati (es. "sede_piano_1"), poi ripiega solo sulla quota
    for (final nome in ['${_norm(edificio)}_${_norm(quota)}', _norm(quota)]) {
      for (final a in _cache!) {
        //se trova corrispondenza con il nome base dell'asset, restituisce il percorso completo
        if (_baseName(a).toLowerCase() == nome) return a;
      }
    }
    //se non trova niente ritorna il fallback
    return fallback;
  }
}
