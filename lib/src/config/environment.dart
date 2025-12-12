enum Environment {
  development,
  production,
}

class EnvironmentConfig {
  final Environment environment;
  final String appName;
  final String appSuffix;
  final String apiBaseUrl;
  final bool enableDebugLogs;

  const EnvironmentConfig({
    required this.environment,
    required this.appName,
    required this.appSuffix,
    required this.apiBaseUrl,
    required this.enableDebugLogs,
  });

  // Development environment
  static const EnvironmentConfig development = EnvironmentConfig(
    environment: Environment.development,
    appName: 'Chord Dev',
    appSuffix: '.dev',
    apiBaseUrl: 'https://api.even.in',
    enableDebugLogs: true,
  );

  // Production environment
  static const EnvironmentConfig production = EnvironmentConfig(
    environment: Environment.production,
    appName: 'Chord',
    appSuffix: '',
    apiBaseUrl: 'https://api.even.in',
    enableDebugLogs: false,
  );

  bool get isDevelopment => environment == Environment.development;
  bool get isProduction => environment == Environment.production;
}
class AppEnvironment {
  static EnvironmentConfig _config = EnvironmentConfig.development;

  static EnvironmentConfig get config => _config;

  static void setEnvironment(EnvironmentConfig config) {
    _config = config;
  }

  static bool get isDevelopment => _config.isDevelopment;
  static bool get isProduction => _config.isProduction;
}
