import 'package:sqflite/sqflite.dart';

class DatabaseHelper {
  static final DatabaseHelper _instance = DatabaseHelper._internal();
  static Database? _database;

  factory DatabaseHelper() => _instance;

  DatabaseHelper._internal();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    final databasesPath = await getDatabasesPath();
    final path = '$databasesPath/chord_sync.db';

    return await openDatabase(path, version: 1, onCreate: _onCreate);
  }

  Future<void> _onCreate(Database db, int version) async {
    // Create synced_files table
    await db.execute('''
      CREATE TABLE synced_files (
        file_path TEXT PRIMARY KEY,
        file_name TEXT NOT NULL,
        target_folder TEXT NOT NULL,
        drive_file_id TEXT NOT NULL,
        file_size INTEGER NOT NULL,
        file_hash TEXT NOT NULL,
        synced_at TEXT NOT NULL,
        last_modified TEXT NOT NULL
      )
    ''');

    // Create sync_metadata table for storing last sync time
    await db.execute('''
      CREATE TABLE sync_metadata (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
      )
    ''');
  }

  Future<void> close() async {
    final db = await database;
    await db.close();
    _database = null;
  }

  Future<void> clearDatabase() async {
    final db = await database;
    await db.delete('synced_files');
    await db.delete('sync_metadata');
  }
}
