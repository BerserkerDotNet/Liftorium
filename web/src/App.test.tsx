import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from './App';

describe('App', () => {
  it('renders the read-only Web surface heading', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Liftorium' })).toBeInTheDocument();
  });

  it('communicates that workout logging is Android-only', () => {
    render(<App />);
    expect(
      screen.getByText(/workout logging happens in the android app/i),
    ).toBeInTheDocument();
  });

  it('omits the build label when none is supplied', () => {
    render(<App />);
    expect(screen.queryByTestId('build-label')).not.toBeInTheDocument();
  });

  it('renders the build label when supplied for snapshot-freshness debugging', () => {
    render(<App buildLabel="phase-1" />);
    expect(screen.getByTestId('build-label')).toHaveTextContent('Build: phase-1');
  });
});
