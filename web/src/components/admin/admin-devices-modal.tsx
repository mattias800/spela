import { Modal, Badge } from "@/components/ui";
import { useAdminUserDevices } from "@/hooks/use-devices";
import { formatRelativeTime } from "@/lib/format";
import { getPlatformIcon } from "@/lib/platform-icons";
import type { User } from "@/types/api";

interface AdminDevicesModalProps {
  user: User;
  onClose: () => void;
}

export function AdminDevicesModal({ user, onClose }: AdminDevicesModalProps) {
  const { data: devices, isLoading } = useAdminUserDevices(user.id);

  return (
    <Modal
      open
      onClose={onClose}
      title={`${user.username}'s Devices`}
      size="md"
    >
      {isLoading ? (
        <div className="space-y-3">
          <div className="h-14 bg-surface-800/50 rounded-lg animate-pulse" />
          <div className="h-14 bg-surface-800/50 rounded-lg animate-pulse" />
        </div>
      ) : !devices || devices.length === 0 ? (
        <p className="text-sm text-surface-400 text-center py-8">No devices registered.</p>
      ) : (
        <div className="space-y-2">
          {devices.map((device) => {
            const PlatformIcon = getPlatformIcon(device.platform);
            return (
              <div key={device.id} className="flex items-center gap-3 rounded-xl border border-surface-800 bg-surface-900/50 px-4 py-3">
                <div className="h-9 w-9 rounded-lg bg-surface-800 flex items-center justify-center">
                  <PlatformIcon className="h-4 w-4 text-surface-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-surface-200 truncate">{device.name}</p>
                  <div className="flex items-center gap-2">
                    <Badge variant="default">{device.platform}</Badge>
                    <span className="text-xs text-surface-500">Last seen {formatRelativeTime(device.lastSeenAt)}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
