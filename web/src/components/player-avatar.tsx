interface PlayerAvatarProps {
  username: string;
  avatarUrl?: string;
}

export function PlayerAvatar({ username, avatarUrl }: PlayerAvatarProps) {
  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={username}
        className="h-8 w-8 rounded-full object-cover"
      />
    );
  }

  return (
    <div className="h-8 w-8 rounded-full bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center text-xs font-bold text-white">
      {username.charAt(0).toUpperCase()}
    </div>
  );
}
