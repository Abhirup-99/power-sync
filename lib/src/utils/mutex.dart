import 'dart:async';

class Mutex {
  Future<void> _last = Future.value();

  Future<T> protect<T>(Future<T> Function() criticalSection) async {
    final previous = _last;
    final completer = Completer<void>();
    _last = completer.future;

    try {
      await previous;
    } catch (_) {
      // Ignore errors from previous tasks
    }

    try {
      return await criticalSection();
    } finally {
      completer.complete();
    }
  }
}
