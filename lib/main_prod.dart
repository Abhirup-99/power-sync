import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';

import 'src/app.dart';
import 'src/config/environment.dart';
import 'src/settings/settings_controller.dart';
import 'src/settings/settings_service.dart';
import 'src/services/debug_log_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  AppEnvironment.setEnvironment(EnvironmentConfig.production);
  await Firebase.initializeApp();

  // Initialize debug logging
  await DebugLogService().initialize();

  final settingsController = SettingsController(SettingsService());
  await settingsController.loadSettings();

  runApp(MyApp(settingsController: settingsController));
}
