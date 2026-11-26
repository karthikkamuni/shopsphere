import { useState } from 'react';
import { createUser } from '../api';
import { newestFirst } from '../format';
import { describeError } from '../messages';
import ServiceColumn from './ServiceColumn';
import { EmptyState, Feedback, Field, SubmitButton } from './ui';

export default function UsersColumn({ resource }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    setBusy(true);
    setFeedback(null);
    try {
      await createUser({ name: name.trim(), email: email.trim() });
      setName('');
      setEmail('');
      setFeedback({ kind: 'ok', text: 'User added.' });
      await resource.reload();
    } catch (error) {
      setFeedback({ kind: 'error', text: describeError('user', error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <ServiceColumn
      tint="users"
      title="Users"
      service="user-service"
      status={resource.status}
      count={resource.items.length}
      onRetry={resource.reload}
    >
      <form onSubmit={handleSubmit} className="space-y-3">
        <Field
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          maxLength={100}
          autoComplete="off"
        />
        <Field
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          maxLength={150}
          autoComplete="off"
        />
        <SubmitButton tint="users" busy={busy}>
          Add user
        </SubmitButton>
        <Feedback feedback={feedback} />
      </form>

      {resource.items.length === 0 ? (
        <EmptyState>No users yet. Add the first one with the form above.</EmptyState>
      ) : (
        <ul className="mt-5 divide-y divide-rule border-t border-rule">
          {newestFirst(resource.items).map((user) => (
            <li key={user.id} className="flex items-baseline justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate font-medium">{user.name}</p>
                <p className="truncate text-sm text-muted">{user.email}</p>
              </div>
              <span className="text-sm tabular-nums text-muted">#{user.id}</span>
            </li>
          ))}
        </ul>
      )}
    </ServiceColumn>
  );
}
