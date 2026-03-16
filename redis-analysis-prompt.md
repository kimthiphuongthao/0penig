# Redis Cluster vs Sentinel Analysis for SSO Lab

## Context
- 3 independent stacks (A, B, C), each with its own Redis instance
- Use case: SLO session blacklist (stores logged-out session IDs)
- Requirements: High availability, data persistence across restarts
- Data pattern:
  - Write-heavy (each logout = 1 write to blacklist)
  - Read-heavy (each request = 1 read check against blacklist)
  - TTL-based expiry (sessions expire automatically)
- Scale: lab environment, under 1000 concurrent sessions
- Each stack is completely independent - no Redis sharing between stacks

## Analysis Requirements

### 1. Comparison for this specific use case
- Redis Cluster: pros/cons for blacklist use case
- Redis Sentinel: pros/cons for blacklist use case
- Single Redis with persistence: is it sufficient?

### 2. Complexity Assessment
- Setup complexity (Docker Compose)
- Operational complexity (monitoring, backup, recovery)
- Code changes required in OpenIG Groovy handlers

### 3. Reliability & Failure Scenarios
- What happens when Redis node fails?
- What happens during network partition?
- Recovery time and data loss scenarios
- Impact on SSO/SLO user experience

### 4. Operational Overhead
- Number of containers required
- Memory/CPU overhead
- Monitoring complexity
- Backup/restore procedures

### 5. Final Recommendation
Given this is a LAB environment (not production), recommend the best approach with:
- Specific reasoning tied to the blacklist use case
- Consideration of operational simplicity vs availability
- Clear verdict: Cluster, Sentinel, or Single Redis with persistence

Output in structured markdown format with clear sections.
