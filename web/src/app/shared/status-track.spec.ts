import { TestBed } from '@angular/core/testing';
import { StatusTrack } from './status-track';
import { ClaimStatus } from '../api/models';

function stagesFor(status: ClaimStatus) {
  const fixture = TestBed.createComponent(StatusTrack);
  fixture.componentRef.setInput('status', status);
  return fixture.componentInstance.stages();
}

describe('StatusTrack', () => {
  it('marks earlier stages done and the current one current', () => {
    expect(stagesFor('UNDER_ASSESSMENT').map(s => s.state)).toEqual(['done', 'done', 'current', 'todo', 'todo']);
  });

  it('flags the assessment stage as waiting on the claimant', () => {
    const stages = stagesFor('AWAITING_INFO');
    expect(stages[2]).toEqual({ label: 'Needs your input', state: 'waiting' });
  });

  it('stops at the decision when rejected and never reaches paid', () => {
    const stages = stagesFor('REJECTED');
    expect(stages[3]).toEqual({ label: 'Not approved', state: 'stopped' });
    expect(stages[4].state).toBe('todo');
  });

  it('completes every stage once settled', () => {
    expect(stagesFor('SETTLED').every(s => s.state === 'done')).toBe(true);
  });
});
