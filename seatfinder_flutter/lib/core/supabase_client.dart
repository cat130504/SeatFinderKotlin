import 'package:supabase_flutter/supabase_flutter.dart';

import 'config.dart';

SupabaseClient createDbClient(String url, String key) =>
    SupabaseClient(url, key);

class AppSupabase {
  static final SupabaseClient client =
      createDbClient(AppConfig.supabaseUrl, AppConfig.supabaseKey);
}
