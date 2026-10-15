import 'package:supabase_flutter/supabase_flutter.dart';

import 'config.dart';

/// Client Supabase "puro" (solo Postgrest), senza Supabase.initialize():
/// l'app non usa Supabase Auth e così lo stesso client è usabile anche
/// nell'isolate del servizio in background.
SupabaseClient createDbClient(String url, String key) =>
    SupabaseClient(url, key);

class AppSupabase {
  static final SupabaseClient client =
      createDbClient(AppConfig.supabaseUrl, AppConfig.supabaseKey);
}
