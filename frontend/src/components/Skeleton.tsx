interface SkeletonProps {
  variant?: 'text' | 'text-short' | 'heading' | 'card' | 'button' | 'circle';
  width?: string;
  height?: string;
  count?: number;
}

export function Skeleton({ variant = 'text', width, height, count = 1 }: SkeletonProps) {
  const items = Array.from({ length: count }, (_, i) => i);

  const getClassName = () => {
    switch (variant) {
      case 'text': return 'skeleton skeleton-text';
      case 'text-short': return 'skeleton skeleton-text-short';
      case 'heading': return 'skeleton skeleton-heading';
      case 'card': return 'skeleton skeleton-card';
      case 'button': return 'skeleton skeleton-button';
      case 'circle': return 'skeleton';
      default: return 'skeleton skeleton-text';
    }
  };

  const style: React.CSSProperties = {};
  if (width) style.width = width;
  if (height) style.height = height;
  if (variant === 'circle') {
    style.width = width ?? '40px';
    style.height = height ?? '40px';
    style.borderRadius = '50%';
  }

  return (
    <>
      {items.map((i) => (
        <div key={i} className={getClassName()} style={style} aria-hidden="true" />
      ))}
    </>
  );
}

/** Pre-composed skeleton for a menu page */
export function MenuPageSkeleton() {
  return (
    <div className="page-container" role="status" aria-label="Loading menu">
      <Skeleton variant="heading" width="70%" />
      <Skeleton variant="text" width="50%" />
      <div style={{ marginTop: 24 }}>
        <Skeleton variant="heading" width="30%" />
        <Skeleton variant="card" count={3} />
      </div>
      <div style={{ marginTop: 24 }}>
        <Skeleton variant="heading" width="30%" />
        <Skeleton variant="card" count={2} />
      </div>
      <div style={{ marginTop: 24 }}>
        <Skeleton variant="button" />
      </div>
    </div>
  );
}

/** Pre-composed skeleton for an order card */
export function OrderCardSkeleton() {
  return (
    <div className="card" style={{ padding: 16 }} role="status" aria-label="Loading order">
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <Skeleton variant="text-short" width="60px" height="24px" />
        <Skeleton variant="text-short" width="80px" height="24px" />
      </div>
      <Skeleton variant="text" count={2} />
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 12 }}>
        <Skeleton variant="text-short" width="70px" />
        <Skeleton variant="button" width="120px" height="36px" />
      </div>
    </div>
  );
}

/** Pre-composed skeleton for a dashboard */
export function DashboardSkeleton() {
  return (
    <div className="page-container-wide" role="status" aria-label="Loading dashboard">
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
        <Skeleton variant="heading" width="200px" />
        <div style={{ display: 'flex', gap: 8 }}>
          <Skeleton variant="button" width="80px" height="36px" />
          <Skeleton variant="button" width="80px" height="36px" />
        </div>
      </div>
      <div style={{ display: 'grid', gap: 12 }}>
        <OrderCardSkeleton />
        <OrderCardSkeleton />
        <OrderCardSkeleton />
      </div>
    </div>
  );
}
