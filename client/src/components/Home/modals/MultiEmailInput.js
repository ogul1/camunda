/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useRef} from 'react';
import classnames from 'classnames';

import {Input, Button, Icon} from 'components';

import './MultiEmailInput.scss';
import {useEffect} from 'react';

export default function MultiEmailInput({emails, onChange, placeholder}) {
  const [errors, setErrors] = useState([]);
  const [value, setValue] = useState('');
  const input = useRef();

  function handleKeyPress(evt) {
    if (['Enter', ' ', 'Tab', ','].includes(evt.key)) {
      if (evt.target.value) {
        evt.preventDefault();
      }
      addEmail(evt);
    }
    if (evt.target.value === '' && evt.key === 'Backspace' && emails.length > 0) {
      const lastElementIndex = emails.length - 1;
      removeEmail(emails[lastElementIndex], lastElementIndex);
    }
  }

  function addEmail(evt) {
    const trimmedValue = evt.target.value.trim();

    if (trimmedValue) {
      const isValid = isValidEmail(trimmedValue);
      if (!isValid) {
        setErrors([...errors, trimmedValue]);
      }

      onChange([...emails, trimmedValue], isValid && errors.length === 0);
      setValue('');
    }
  }

  function removeEmail(email, index) {
    const newEmails = emails.filter((_, i) => i !== index);
    const errorIndex = errors.indexOf(email);
    const newErrors = errors.filter((_, i) => i !== errorIndex);
    setErrors(newErrors);
    onChange(newEmails, newErrors.length === 0);
  }

  function triggerClear(evt) {
    if (evt.type === 'keydown' && evt.keyCode !== 13) {
      return;
    }
    setErrors([]);
    onChange([], true);
    if (input.current) {
      input.current.focus();
    }
    evt.preventDefault();
  }

  function handlePaste(evt) {
    evt.preventDefault();

    const paste = (evt.clipboardData || window.clipboardData).getData('text');
    const newEmails = paste.match(/[^\s<>!?:;]+/g);

    if (newEmails) {
      if (newEmails.length > 20) {
        newEmails.length = 20;
      }
      const invalidEmails = newEmails.filter((email) => !isValidEmail(email));
      setErrors([...errors, ...invalidEmails]);
      const allEmailsValid = errors.length === 0 && invalidEmails.length === 0;
      onChange([...emails, ...newEmails], allEmailsValid);
    }
  }

  function resize() {
    if (input.current) {
      input.current.style.width = (input.current.value.length + 1) * 10 + 'px';
    }
  }

  useEffect(() => {
    resize();
  }, [emails]);

  return (
    <div className="MultiEmailInput" onClick={() => input.current?.focus()}>
      {emails.length === 0 && value === '' && <span className="placeholder">{placeholder}</span>}
      {emails.map((email, i) => (
        <div key={i} className={classnames('tag', {error: errors.includes(email)})}>
          <span className="tagText" title={email}>
            {email}
          </span>
          <Button icon className="close" onClick={() => removeEmail(email, i)}>
            <Icon type="close-large" size="10px" />
          </Button>
        </div>
      ))}
      {emails.length < 20 && (
        <Input
          value={value}
          ref={input}
          onKeyDown={handleKeyPress}
          onBlur={addEmail}
          onPaste={handlePaste}
          onInput={resize}
          onChange={(evt) => setValue(evt.target.value)}
        />
      )}
      {emails.length > 0 && (
        <button className="searchClear" onKeyDown={triggerClear} onMouseDown={triggerClear}>
          <Icon type="clear" />
        </button>
      )}
    </div>
  );
}

function isValidEmail(email) {
  return /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/.test(email);
}
