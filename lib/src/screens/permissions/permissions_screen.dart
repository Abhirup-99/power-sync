import 'package:flutter/material.dart';
import '../../services/permission_service.dart';

/// Screen shown after login to request necessary permissions
class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  static const routeName = '/permissions';

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen> {
  final PermissionService _permissionService = PermissionService();
  bool _isRequesting = false;
  List<String> _deniedPermissions = [];

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    final hasAll = await _permissionService.hasAllPermissions();
    if (hasAll && mounted) {
      // All permissions granted, navigate to onboarding
      Navigator.of(context).pushReplacementNamed('/onboarding');
      return;
    }

    final denied = await _permissionService.getDeniedPermissions();
    if (mounted) {
      setState(() {
        _deniedPermissions = denied;
      });
    }
  }

  Future<void> _requestPermissions() async {
    setState(() {
      _isRequesting = true;
    });

    try {
      final granted = await _permissionService.requestAllPermissions();

      if (!mounted) return;

      if (granted) {
        // All critical permissions granted, proceed to onboarding
        Navigator.of(context).pushReplacementNamed('/onboarding');
      } else {
        // Some permissions denied, show which ones
        final denied = await _permissionService.getDeniedPermissions();
        if (!mounted) return;
        
        setState(() {
          _deniedPermissions = denied;
        });

        // Show message about denied permissions
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Some permissions were denied. The app may not work properly without them.',
            ),
            backgroundColor: Colors.orange,
            duration: Duration(seconds: 4),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isRequesting = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 40),
              Icon(
                Icons.security,
                size: 64,
                color: theme.colorScheme.primary,
              ),
              const SizedBox(height: 24),
              Text(
                'Permissions Required',
                style: theme.textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              Text(
                'Chord needs the following permissions to work properly:',
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: Colors.grey[700],
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              _buildPermissionItem(
                icon: Icons.phone,
                title: 'Phone & Call Logs',
                description: 'To backup your call history to the cloud',
              ),
              const SizedBox(height: 12),
              _buildPermissionItem(
                icon: Icons.folder,
                title: 'Storage & Files',
                description: 'To access and backup your recordings',
              ),
              const SizedBox(height: 12),
              _buildPermissionItem(
                icon: Icons.notifications,
                title: 'Notifications',
                description: 'To notify you about sync status',
              ),
              const SizedBox(height: 12),
              _buildPermissionItem(
                icon: Icons.battery_charging_full,
                title: 'Battery Optimization',
                description: 'To keep background sync running',
              ),
              const SizedBox(height: 32),
              if (_deniedPermissions.isNotEmpty) ...[
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.orange.shade50,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.orange.shade200),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.warning, color: Colors.orange.shade700),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Missing: ${_deniedPermissions.join(", ")}',
                          style: TextStyle(
                            color: Colors.orange.shade700,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
              ],
              ElevatedButton(
                onPressed: _isRequesting ? null : _requestPermissions,
                style: ElevatedButton.styleFrom(
                  backgroundColor: theme.colorScheme.primary,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: _isRequesting
                    ? const SizedBox(
                        height: 20,
                        width: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                        ),
                      )
                    : const Text(
                        'Grant Permissions',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
              ),
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPermissionItem({
    required IconData icon,
    required String title,
    required String description,
  }) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey.shade200),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary.withAlpha(25),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              icon,
              color: Theme.of(context).colorScheme.primary,
              size: 22,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  description,
                  style: TextStyle(
                    color: Colors.grey[600],
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
