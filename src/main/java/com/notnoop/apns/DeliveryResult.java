package com.notnoop.apns;

public class DeliveryResult {
	private DeliveryError error;
	private int id;

	public DeliveryResult(DeliveryError error, int id) {
		this.error = error;
		this.id = id;
	}

	public DeliveryError getError() {
		return error;
	}

	public void setError(DeliveryError error) {
		this.error = error;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((error == null) ? 0 : error.hashCode());
		result = prime * result + id;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DeliveryResult other = (DeliveryResult) obj;
		if (error != other.error)
			return false;
		if (id != other.id)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("DeliveryResult [error=%s, id=%s]", error, id);
	}
}
