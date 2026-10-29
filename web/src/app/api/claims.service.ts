import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ClaimDetail, ClaimStatus, ClaimSummary, DemoUser, Exposure, LoginResponse,
  SubmitClaimRequest, Workload,
} from './models';

/** Thin typed wrapper over both backends. Paths are relative; the dev proxy / nginx routes them. */
@Injectable({ providedIn: 'root' })
export class ClaimsApi {
  private http = inject(HttpClient);

  // --- auth (dev)
  demoUsers(): Observable<DemoUser[]> {
    return this.http.get<DemoUser[]>('/api/auth/users');
  }

  login(username: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username });
  }

  // --- claimant
  submit(body: SubmitClaimRequest): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>('/api/claims', body);
  }

  mine(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims/mine');
  }

  respond(claimId: string, requestId: string, response: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${claimId}/info-requests/${requestId}/response`, { response });
  }

  // --- shared
  get(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`/api/claims/${id}`);
  }

  // --- staff
  queue(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims/queue');
  }

  assigned(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims/assigned');
  }

  search(status?: ClaimStatus): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims', { params: status ? { status } : {} });
  }

  pickUp(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/pick-up`, {});
  }

  startAssessment(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/start-assessment`, {});
  }

  requestInfo(id: string, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/info-requests`, { question });
  }

  updateReserve(id: string, amount: number, reason: string): Observable<ClaimDetail> {
    return this.http.put<ClaimDetail>(`/api/claims/${id}/reserve`, { amount, reason });
  }

  approve(id: string, amount: number, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/approve`, { amount, reason });
  }

  reject(id: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/reject`, { reason });
  }

  settle(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`/api/claims/${id}/settle`, {});
  }

  // --- insights
  exposure(team?: string): Observable<Exposure> {
    return this.http.get<Exposure>('/api/insights/exposure', { params: team ? { team } : {} });
  }

  workload(team?: string): Observable<Workload> {
    return this.http.get<Workload>('/api/insights/workload', { params: team ? { team } : {} });
  }
}
