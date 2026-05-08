import { Component, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { StationMenuPage } from './pages/customer/StationMenuPage';
import { OrderBuilderPage } from './pages/customer/OrderBuilderPage';
import { CheckoutPage } from './pages/customer/CheckoutPage';
import { OrderTrackingPage } from './pages/customer/OrderTrackingPage';
import { OfflinePage } from './pages/customer/OfflinePage';
import { LoginPage } from './pages/vendor/LoginPage';
import { DashboardPage } from './pages/vendor/DashboardPage';
import { MenuManagementPage } from './pages/vendor/MenuManagementPage';
import { SettingsPage } from './pages/vendor/SettingsPage';
import { ThemeToggle } from './components/ThemeToggle';
import { ConnectionStatus } from './components/ConnectionStatus';

class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) { return { error }; }
  render() {
    if (this.state.error) {
      return (
        <div className="page-container" style={{ paddingTop: 40, textAlign: 'center' }}>
          <h1 style={{ color: 'var(--color-danger)', marginBottom: 16 }}>Something went wrong</h1>
          <pre className="card" style={{ textAlign: 'left', overflow: 'auto', fontSize: '0.85rem', padding: 16 }}>
            {this.state.error.message}{'\n'}{this.state.error.stack}
          </pre>
        </div>
      );
    }
    return this.props.children;
  }
}

function AppHeader() {
  return (
    <header className="app-header">
      <h1>DrinkSync</h1>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <ThemeToggle />
      </div>
    </header>
  );
}

function AnimatedRoutes() {
  const location = useLocation();

  return (
    <div key={location.pathname} className="page-fade-in">
      <Routes>
        {/* Customer routes */}
        <Route path="/station/:stationId" element={<StationMenuPage />} />
        <Route path="/station/:stationId/order" element={<OrderBuilderPage />} />
        <Route path="/station/:stationId/checkout/:orderId" element={<CheckoutPage />} />
        <Route path="/station/:stationId/tracking" element={<OrderTrackingPage />} />
        <Route path="/offline" element={<OfflinePage />} />

        {/* Vendor routes */}
        <Route path="/vendor/login" element={<LoginPage />} />
        <Route path="/vendor/dashboard" element={<DashboardPage />} />
        <Route path="/vendor/menu" element={<MenuManagementPage />} />
        <Route path="/vendor/settings" element={<SettingsPage />} />

        {/* Default redirect */}
        <Route path="/" element={<Navigate to="/vendor/login" replace />} />
      </Routes>
    </div>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AppHeader />
        <ConnectionStatus />
        <AnimatedRoutes />
      </BrowserRouter>
    </ErrorBoundary>
  );
}

export default App;
